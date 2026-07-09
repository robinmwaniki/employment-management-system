package com.ems.controller;

import com.ems.dto.response.DashboardResponse;
import com.ems.service.interfaces.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardRestController {

    private final DashboardService dashboardService;

    @GetMapping
    public DashboardResponse getDashboardStatistics() {

        return dashboardService.getDashboardStatistics();
    }
}