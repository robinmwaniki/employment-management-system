package com.ems.repository;

import com.ems.entity.Employee;
import com.ems.entity.PerformanceReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PerformanceRepository
        extends JpaRepository<PerformanceReview, Long> {

    Page<PerformanceReview> findAll(Pageable pageable);

    boolean existsByEmployeeAndReviewDateAndReviewer(
            Employee employee, java.time.LocalDate reviewDate, String reviewer);

    Page<PerformanceReview> findByEmployee(
            Employee employee,
            Pageable pageable);

}