package com.ems;

import com.ems.entity.Department;
import com.ems.entity.Employee;
import com.ems.entity.LeaveRequestEntity;
import com.ems.entity.LeaveStatus;
import com.ems.entity.LeaveType;
import com.ems.entity.Payroll;
import com.ems.repository.DepartmentRepository;
import com.ems.repository.EmployeeRepository;
import com.ems.repository.LeaveRepository;
import com.ems.repository.PayrollRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PostgreSQL-backed tests for optimistic locking, employee-scoped leave locks,
 * payroll idempotency and rollback of losing concurrent writes.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class WorkflowConcurrencyIntegrationTests {

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private LeaveRepository leaveRepository;

    @Autowired
    private PayrollRepository payrollRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void staleEmployeeUpdateIsRejectedInsteadOfOverwritingTheCommittedValue() {
        Employee employee = createEmployee();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        Employee firstSnapshot = transaction.execute(status ->
                employeeRepository.findById(employee.getId()).orElseThrow());
        Employee staleSnapshot = transaction.execute(status ->
                employeeRepository.findById(employee.getId()).orElseThrow());

        firstSnapshot.setSalary(new BigDecimal("81000.00"));
        transaction.executeWithoutResult(status ->
                employeeRepository.saveAndFlush(firstSnapshot));

        staleSnapshot.setSalary(new BigDecimal("92000.00"));
        assertThatThrownBy(() -> transaction.executeWithoutResult(status ->
                employeeRepository.saveAndFlush(staleSnapshot)))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        Employee persisted = employeeRepository.findById(employee.getId()).orElseThrow();
        assertThat(persisted.getSalary()).isEqualByComparingTo("81000.00");
        assertThat(persisted.getVersion()).isEqualTo(1L);
    }

    @Test
    void concurrentPayrollGenerationLeavesExactlyOneRecordAndRollsBackTheLoser()
            throws Exception {
        Employee employee = createEmployee();
        YearMonth month = YearMonth.of(2026, 11);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        try {
            Future<RuntimeException> first = executor.submit(() ->
                    attemptPayrollInsert(employee, month, ready, start, transaction));
            Future<RuntimeException> second = executor.submit(() ->
                    attemptPayrollInsert(employee, month, ready, start, transaction));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            RuntimeException firstFailure = first.get(15, TimeUnit.SECONDS);
            RuntimeException secondFailure = second.get(15, TimeUnit.SECONDS);

            assertThat(firstFailure == null ^ secondFailure == null).isTrue();
            RuntimeException losingFailure =
                    firstFailure == null ? secondFailure : firstFailure;
            assertThat(losingFailure).isInstanceOf(DataIntegrityViolationException.class);
            assertThat(payrollRepository.findByEmployeeAndPayrollMonth(employee, month))
                    .isPresent();
            assertThat(payrollRepository.findByEmployee(employee).stream()
                    .filter(payroll -> payroll.getPayrollMonth().equals(month))
                    .count()).isEqualTo(1L);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void employeeRowLockSerializesOverlappingLeaveApplications() throws Exception {
        Employee employee = createEmployee();
        LocalDate startDate = LocalDate.now().plusDays(10);
        LocalDate endDate = startDate.plusDays(3);
        CountDownLatch firstHasInserted = new CountDownLatch(1);
        CountDownLatch secondIsAboutToLock = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        try {
            Future<Boolean> first = executor.submit(() -> transaction.execute(status -> {
                Employee locked = employeeRepository.findByIdForUpdate(employee.getId())
                        .orElseThrow();
                boolean overlap = hasOverlappingLeave(locked, startDate, endDate);
                if (!overlap) {
                    leaveRepository.saveAndFlush(LeaveRequestEntity.builder()
                            .employee(locked)
                            .leaveType(LeaveType.ANNUAL)
                            .startDate(startDate)
                            .endDate(endDate)
                            .status(LeaveStatus.PENDING)
                            .appliedDate(LocalDate.now())
                            .build());
                }
                firstHasInserted.countDown();
                await(releaseFirst);
                return overlap;
            }));

            assertThat(firstHasInserted.await(5, TimeUnit.SECONDS)).isTrue();

            Future<Boolean> second = executor.submit(() -> transaction.execute(status -> {
                secondIsAboutToLock.countDown();
                Employee locked = employeeRepository.findByIdForUpdate(employee.getId())
                        .orElseThrow();
                return hasOverlappingLeave(locked, startDate, endDate);
            }));

            assertThat(secondIsAboutToLock.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> second.get(200, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            releaseFirst.countDown();

            assertThat(first.get(10, TimeUnit.SECONDS)).isFalse();
            assertThat(second.get(10, TimeUnit.SECONDS)).isTrue();
            assertThat(leaveRepository.findByEmployee(employee,
                    org.springframework.data.domain.PageRequest.of(0, 10)).getTotalElements())
                    .isEqualTo(1L);
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
    }

    private RuntimeException attemptPayrollInsert(
            Employee employee,
            YearMonth month,
            CountDownLatch ready,
            CountDownLatch start,
            TransactionTemplate transaction) throws InterruptedException {

        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent payroll test did not start.");
        }

        try {
            transaction.executeWithoutResult(status ->
                    payrollRepository.saveAndFlush(Payroll.builder()
                            .employee(employee)
                            .payrollMonth(month)
                            .basicSalary(new BigDecimal("75000.00"))
                            .houseAllowance(new BigDecimal("10000.00"))
                            .transportAllowance(new BigDecimal("5000.00"))
                            .bonus(BigDecimal.ZERO)
                            .deductions(BigDecimal.ZERO)
                            .tax(BigDecimal.ZERO)
                            .netSalary(new BigDecimal("90000.00"))
                            .build()));
            return null;
        } catch (RuntimeException exception) {
            return exception;
        }
    }

    private boolean hasOverlappingLeave(
            Employee employee,
            LocalDate startDate,
            LocalDate endDate) {
        return leaveRepository
                .existsByEmployeeAndStatusInAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        employee,
                        java.util.List.of(LeaveStatus.PENDING, LeaveStatus.APPROVED),
                        endDate,
                        startDate);
    }

    private Employee createEmployee() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Department department = departmentRepository.saveAndFlush(Department.builder()
                .name("Concurrency " + suffix)
                .code("C" + suffix)
                .build());

        return employeeRepository.saveAndFlush(Employee.builder()
                .firstName("Test")
                .lastName("Employee")
                .email("concurrency-" + suffix + "@example.test")
                .phoneNumber("07" + suffix.substring(0, 8))
                .salary(new BigDecimal("75000.00"))
                .hireDate(LocalDate.now().minusYears(1))
                .department(department)
                .build());
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for concurrent transaction.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrent transaction interrupted.", exception);
        }
    }
}
