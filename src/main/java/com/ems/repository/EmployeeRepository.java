package com.ems.repository;

import com.ems.entity.Employee;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    Optional<Employee> findByEmail(String email);

    /** Serializes leave-period checks for the same employee inside a transaction. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Employee e where e.id = :employeeId")
    Optional<Employee> findByIdForUpdate(@Param("employeeId") Long employeeId);

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