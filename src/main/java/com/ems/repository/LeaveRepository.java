package com.ems.repository;

import com.ems.entity.Employee;
import com.ems.entity.LeaveRequestEntity;
import com.ems.entity.LeaveStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface LeaveRepository extends JpaRepository<LeaveRequestEntity, Long> {

    Page<LeaveRequestEntity> findByEmployee(Employee employee, Pageable pageable);

    Page<LeaveRequestEntity> findByStatus(LeaveStatus status, Pageable pageable);

    long countByStatus(LeaveStatus status);

    boolean existsByEmployeeAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            Employee employee,
            LocalDate endDate,
            LocalDate startDate
    );
}