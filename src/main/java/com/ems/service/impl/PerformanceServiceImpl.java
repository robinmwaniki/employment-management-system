package com.ems.service.impl;

import com.ems.dto.request.PerformanceRequest;
import com.ems.dto.response.PerformanceResponse;
import com.ems.entity.Employee;
import com.ems.entity.PerformanceReview;
import com.ems.exception.ResourceNotFoundException;
import com.ems.mapper.PerformanceMapper;
import com.ems.repository.EmployeeRepository;
import com.ems.repository.PerformanceRepository;
import com.ems.service.interfaces.NotificationService;
import com.ems.service.interfaces.PerformanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PerformanceServiceImpl
        implements PerformanceService {

    private final PerformanceRepository performanceRepository;
    private final EmployeeRepository employeeRepository;
    private final NotificationService notificationService;

    @Override
    public PerformanceResponse createReview(
            PerformanceRequest request) {

        Employee employee = employeeRepository.findById(
                        request.getEmployeeId())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Employee not found"));

        PerformanceReview review = PerformanceReview.builder()
                .employee(employee)
                .reviewDate(request.getReviewDate())
                .reviewer(request.getReviewer())
                .rating(request.getRating())
                .comments(request.getComments())
                .goals(request.getGoals())
                .build();

        PerformanceReview saved =
                performanceRepository.save(review);

        notificationService.create(
                "Performance Review",
                "A new performance review has been added.");

        return PerformanceMapper.toResponse(saved);
    }

    @Override
    public PerformanceResponse updateReview(
            Long id,
            PerformanceRequest request) {

        PerformanceReview review =
                performanceRepository.findById(id)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Performance review not found"));

        Employee employee = employeeRepository.findById(
                        request.getEmployeeId())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Employee not found"));

        review.setEmployee(employee);
        review.setReviewDate(request.getReviewDate());
        review.setReviewer(request.getReviewer());
        review.setRating(request.getRating());
        review.setComments(request.getComments());
        review.setGoals(request.getGoals());

        PerformanceReview updated =
                performanceRepository.save(review);

        return PerformanceMapper.toResponse(updated);
    }

    @Override
    public void deleteReview(Long id) {

        PerformanceReview review =
                performanceRepository.findById(id)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Performance review not found"));

        performanceRepository.delete(review);
    }

    @Override
    public PerformanceResponse getReviewById(Long id) {

        PerformanceReview review =
                performanceRepository.findById(id)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Performance review not found"));

        return PerformanceMapper.toResponse(review);
    }

    @Override
    public Page<PerformanceResponse> getAllReviews(
            int page,
            int size) {

        Pageable pageable = PageRequest.of(page, size);

        return performanceRepository
                .findAll(pageable)
                .map(PerformanceMapper::toResponse);
    }

    @Override
    public Page<PerformanceResponse> getEmployeeReviews(
            Long employeeId,
            int page,
            int size) {

        Employee employee =
                employeeRepository.findById(employeeId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Employee not found"));

        Pageable pageable = PageRequest.of(page, size);

        return performanceRepository
                .findByEmployee(employee, pageable)
                .map(PerformanceMapper::toResponse);
    }

}