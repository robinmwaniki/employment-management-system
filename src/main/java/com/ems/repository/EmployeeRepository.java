package com.ems.repository;

import com.ems.entity.Employee;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    Optional<Employee> findByEmail(String email);

    Optional<Employee> findByPhoneNumber(String phoneNumber);

    boolean existsByEmail(String email);

    boolean existsByPhoneNumber(String phoneNumber);

    Page<Employee> findAll(Pageable pageable);

    Page<Employee> findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(
            String firstName,
            String lastName,
            Pageable pageable
    );

    long countByHireDateAfter(LocalDate date);

    @Query("SELECT COALESCE(SUM(e.salary), 0) FROM Employee e")
    BigDecimal getTotalPayroll();

    @Query("SELECT MAX(e.salary) FROM Employee e")
    BigDecimal getHighestSalary();

    @Query("SELECT MIN(e.salary) FROM Employee e")
    BigDecimal getLowestSalary();

    @Query("SELECT AVG(e.salary) FROM Employee e")
    BigDecimal getAverageSalary();

    // Dashboard - Latest 5 employees
    List<Employee> findTop5ByOrderByIdDesc();

}