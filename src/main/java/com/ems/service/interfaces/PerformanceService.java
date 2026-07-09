package com.ems.service.interfaces;

import com.ems.dto.request.PerformanceRequest;
import com.ems.dto.response.PerformanceResponse;

import java.util.List;

public interface PerformanceService {

    PerformanceResponse addReview(PerformanceRequest request);

    PerformanceResponse getReview(Long id);

    List<PerformanceResponse> getEmployeeReviews(Long employeeId);

}