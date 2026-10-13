package com.ems.repository;

import com.ems.entity.Employee;
import com.ems.entity.LeaveRequestEntity;
import com.ems.entity.LeaveStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;

public interface LeaveRepository extends JpaRepository<LeaveRequestEntity, Long> {

    Page<LeaveRequestEntity> findByEmployee(Employee employee, Pageable pageable);

    Page<LeaveRequestEntity> findByStatus(LeaveStatus status, Pageable pageable);

    long countByStatus(LeaveStatus status);

    /**
     * True when the employee already has a request in one of the given statuses
     * that overlaps the period from startDate to endDate.
     */
    boolean existsByEmployeeAndStatusInAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            Employee employee,
            Collection<LeaveStatus> statuses,
            LocalDate endDate,
            LocalDate startDate
    );

}
