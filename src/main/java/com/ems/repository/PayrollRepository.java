package com.ems.repository;

import com.ems.entity.Employee;
import com.ems.entity.Payroll;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

public interface PayrollRepository extends JpaRepository<Payroll, Long> {

    List<Payroll> findByEmployee(Employee employee);

    Optional<Payroll> findByEmployeeAndPayrollMonth(
            Employee employee,
            YearMonth payrollMonth
    );

}