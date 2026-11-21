package com.ems.demo;

import com.ems.entity.*;
import com.ems.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.UUID;
import com.ems.service.impl.KenyanPayrollCalculator;

/**
 * Creates repeatable synthetic demo data. Never runs outside the demo profile.
 * Demo account passwords are random and cannot be used for password login;
 * DemoAccessController issues scoped demo tokens instead.
 */
@Component
@Profile("demo")
@RequiredArgsConstructor
public class DemoDataInitializer implements ApplicationRunner {

    private static final ZoneId NAIROBI = ZoneId.of("Africa/Nairobi");

    private final DepartmentRepository departmentRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final AnnouncementRepository announcementRepository;
    private final AttendanceRepository attendanceRepository;
    private final LeaveRepository leaveRepository;
    private final PayrollRepository payrollRepository;
    private final PerformanceRepository performanceRepository;
    private final CompanyProfileRepository companyProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final KenyanPayrollCalculator payrollCalculator;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Department operations = department("Operations", "DEMO-OPS");
        Department finance = department("Finance", "DEMO-FIN");
        Department people = department("People & Culture", "DEMO-HR");

        Employee employee = employee(
                "Amina", "Kamau", "amina.kamau@example.test",
                "+254700000101", "85000.00", people);
        employee("Brian", "Otieno", "brian.otieno@example.test",
                "+254700000102", "112000.00", operations);
        employee("Wanjiku", "Njeri", "wanjiku.njeri@example.test",
                "+254700000103", "97000.00", finance);
        employee("Kevin", "Mwangi", "kevin.mwangi@example.test",
                "+254700000104", "76000.00", operations);

        demoUser("demo-admin", Role.ROLE_ADMIN, null);
        demoUser("demo-hr", Role.ROLE_HR_MANAGER, null);
        demoUser("demo-employee", Role.ROLE_EMPLOYEE, employee);

        seedAnnouncements();
        seedAttendance(employee);
        seedLeave(employee);
        seedPayroll(employee);
        seedPerformance(employee);

        companyProfileRepository.findById(1).ifPresent(company -> {
            company.setCompanyName("Demo Workforce Ltd.");
            company.setKraPin(null);
            company.setNssfEmployerNumber(null);
            company.setShaEmployerNumber(null);
            company.setAddress("Demo address, Nairobi, Kenya");
            company.setPhoneNumber("+254700000100");
            company.setEmail("hello@example.test");
        });
    }

    private Department department(String name, String code) {
        return departmentRepository.findByCode(code).orElseGet(() ->
                departmentRepository.save(Department.builder()
                        .name(name)
                        .code(code)
                        .build()));
    }

    private Employee employee(
            String firstName,
            String lastName,
            String email,
            String phone,
            String salary,
            Department department) {
        return employeeRepository.findByEmail(email).orElseGet(() ->
                employeeRepository.save(Employee.builder()
                        .firstName(firstName)
                        .lastName(lastName)
                        .email(email)
                        .phoneNumber(phone)
                        .salary(new BigDecimal(salary))
                        .hireDate(LocalDate.now(NAIROBI).minusMonths(8))
                        .department(department)
                        .build()));
    }

    private void demoUser(String username, Role role, Employee employee) {
        if (userRepository.existsByUsername(username)) {
            return;
        }
        userRepository.save(User.builder()
                .username(username)
                .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                .role(role)
                .employee(employee)
                .enabled(true)
                .build());
    }

    private void seedAnnouncements() {
        if (announcementRepository.count() > 0) {
            return;
        }
        LocalDate today = LocalDate.now(NAIROBI);
        announcementRepository.save(Announcement.builder()
                .title("Welcome to the EMS demo")
                .message("This environment contains synthetic sample records. Explore the HR and employee workflows; all changes are disabled.")
                .publishDate(today.minusDays(2))
                .expiryDate(today.plusMonths(2))
                .build());
        announcementRepository.save(Announcement.builder()
                .title("November team update")
                .message("The Operations and Finance teams have completed their monthly review. This is sample announcement content.")
                .publishDate(today.minusDays(1))
                .expiryDate(today.plusMonths(1))
                .build());
    }

    private void seedAttendance(Employee employee) {
        LocalDate today = LocalDate.now(NAIROBI);
        for (int daysAgo = 1; daysAgo <= 5; daysAgo++) {
            LocalDate date = today.minusDays(daysAgo);
            if (attendanceRepository.findByEmployeeAndAttendanceDate(employee, date).isPresent()) {
                continue;
            }
            LocalDateTime checkIn = LocalDateTime.of(date, LocalTime.of(8, 45));
            LocalDateTime checkOut = LocalDateTime.of(date, LocalTime.of(17, 15));
            attendanceRepository.save(Attendance.builder()
                    .employee(employee)
                    .attendanceDate(date)
                    .checkInTime(checkIn)
                    .checkOutTime(checkOut)
                    .workingHours(Duration.between(checkIn, checkOut))
                    .late(false)
                    .overtime(false)
                    .build());
        }
    }

    private void seedLeave(Employee employee) {
        if (leaveRepository.count() > 0) {
            return;
        }
        LocalDate today = LocalDate.now(NAIROBI);
        leaveRepository.save(LeaveRequestEntity.builder()
                .employee(employee)
                .leaveType(LeaveType.ANNUAL)
                .startDate(today.plusDays(12))
                .endDate(today.plusDays(14))
                .reason("Planned personal time (synthetic demo request)")
                .status(LeaveStatus.PENDING)
                .appliedDate(today.minusDays(1))
                .build());
        leaveRepository.save(LeaveRequestEntity.builder()
                .employee(employee)
                .leaveType(LeaveType.SICK)
                .startDate(today.minusDays(18))
                .endDate(today.minusDays(17))
                .reason("Sample approved leave record")
                .status(LeaveStatus.APPROVED)
                .appliedDate(today.minusDays(20))
                .build());
    }

    private void seedPayroll(Employee employee) {
        YearMonth month = YearMonth.now(NAIROBI).minusMonths(1);
        if (payrollRepository.findByEmployeeAndPayrollMonth(employee, month).isPresent()) {
            return;
        }
        BigDecimal basic = employee.getSalary();
        BigDecimal house = new BigDecimal("10000.00");
        BigDecimal transport = new BigDecimal("5000.00");
        BigDecimal bonus = BigDecimal.ZERO;
        KenyanPayrollCalculator.Result calculation = payrollCalculator.calculate(
                month, basic, house, transport, bonus, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, true);
        payrollRepository.save(Payroll.builder()
                .employee(employee)
                .payrollMonth(month)
                .basicSalary(basic)
                .houseAllowance(house)
                .transportAllowance(transport)
                .bonus(bonus)
                .grossPay(calculation.gross())
                .taxablePay(calculation.taxable())
                .nssf(calculation.nssf())
                .shif(calculation.shif())
                .housingLevy(calculation.housing())
                .tax(calculation.paye())
                .deductions(calculation.otherDeductions())
                .netSalary(calculation.net())
                .personalRelief(calculation.personalRelief())
                .insuranceRelief(calculation.insuranceRelief())
                .insurancePremium(BigDecimal.ZERO)
                .employerNssf(calculation.employerNssf())
                .employerHousingLevy(calculation.employerHousing())
                .pensionContribution(calculation.pension())
                .residentTaxpayer(true)
                .build());
    }

    private void seedPerformance(Employee employee) {
        if (performanceRepository.count() > 0) {
            return;
        }
        performanceRepository.save(PerformanceReview.builder()
                .employee(employee)
                .reviewDate(LocalDate.now(NAIROBI).minusDays(30))
                .reviewer("Demo HR Manager")
                .rating(4)
                .comments("Strong collaboration and consistent delivery in the sample review period.")
                .goals("Improve cross-team reporting and complete the next skills development milestone.")
                .build());
    }
}
