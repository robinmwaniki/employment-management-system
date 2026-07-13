package com.ems.controller;

import com.ems.dto.request.PerformanceRequest;
import com.ems.dto.response.PerformanceResponse;
import com.ems.service.interfaces.PerformanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/performance")
@RequiredArgsConstructor
public class PerformanceController {

    private final PerformanceService performanceService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PerformanceResponse createReview(
            @RequestBody PerformanceRequest request) {

        return performanceService.createReview(request);
    }

    @GetMapping("/{id}")
    public PerformanceResponse getReview(
            @PathVariable Long id) {

        return performanceService.getReviewById(id);
    }

    @GetMapping
    public Page<PerformanceResponse> getAllReviews(

            @RequestParam(defaultValue = "0") int page,

            @RequestParam(defaultValue = "10") int size) {

        return performanceService.getAllReviews(page, size);
    }

    @GetMapping("/employee/{employeeId}")
    public Page<PerformanceResponse> getEmployeeReviews(

            @PathVariable Long employeeId,

            @RequestParam(defaultValue = "0") int page,

            @RequestParam(defaultValue = "10") int size) {

        return performanceService.getEmployeeReviews(
                employeeId,
                page,
                size);
    }

    @PutMapping("/{id}")
    public PerformanceResponse updateReview(
            @PathVariable Long id,
            @RequestBody PerformanceRequest request) {

        return performanceService.updateReview(id, request);
    }

    @DeleteMapping("/{id}")
    public void deleteReview(
            @PathVariable Long id) {

        performanceService.deleteReview(id);
    }
}