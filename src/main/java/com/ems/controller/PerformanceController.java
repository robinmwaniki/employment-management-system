package com.ems.controller;

import com.ems.dto.request.PerformanceRequest;
import com.ems.dto.response.PerformanceResponse;
import com.ems.service.interfaces.PerformanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/performance")
@RequiredArgsConstructor
public class PerformanceController {

    private final PerformanceService performanceService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PerformanceResponse addReview(
            @Valid @RequestBody PerformanceRequest request) {

        return performanceService.addReview(request);
    }

    @GetMapping("/{id}")
    public PerformanceResponse getReview(
            @PathVariable Long id) {

        return performanceService.getReview(id);
    }

    @GetMapping("/employee/{employeeId}")
    public List<PerformanceResponse> getEmployeeReviews(
            @PathVariable Long employeeId) {

        return performanceService.getEmployeeReviews(employeeId);
    }
}