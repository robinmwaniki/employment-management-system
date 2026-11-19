package com.ems.repository;

import com.ems.entity.Employee;
import com.ems.entity.Payroll;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

public interface PayrollRepository extends JpaRepository<Payroll, Long> {

    List<Payroll> findByEmployee(Employee employee);

    /**
     * Retrieves an employee's payroll records using database-level pagination.
     *
     * @param employee employee whose payroll records should be retrieved
     * @param pageable page number, size, and sorting configuration
     * @return a page of payroll records belonging to the employee
     */
    Page<Payroll> findByEmployee(Employee employee, Pageable pageable);

    Optional<Payroll> findByEmployeeAndPayrollMonth(
            Employee employee,
            YearMonth payrollMonth
    );

}