package com.ems.service.interfaces;

import com.ems.dto.request.PerformanceRequest;
import com.ems.dto.response.PerformanceResponse;
import org.springframework.data.domain.Page;

public interface PerformanceService {

    PerformanceResponse createReview(PerformanceRequest request);

    PerformanceResponse updateReview(
            Long id,
            PerformanceRequest request);

    void deleteReview(Long id);

    PerformanceResponse getReviewById(Long id);

    Page<PerformanceResponse> getAllReviews(
            int page,
            int size);

    Page<PerformanceResponse> getEmployeeReviews(
            Long employeeId,
            int page,
            int size);
}