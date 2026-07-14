package com.ems.controller;

import com.ems.dto.response.AttendanceResponse;
import com.ems.dto.response.EmployeeResponse;
import com.ems.dto.response.LeaveResponse;
import com.ems.dto.response.PayrollResponse;
import com.ems.dto.response.PerformanceResponse;
import com.ems.service.interfaces.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @PreAuthorize("hasAnyRole('ADMIN','HR_MANAGER')")
    @GetMapping("/employees")
    public List<EmployeeResponse> employees() {
        return reportService.employeeReport();
    }

    @PreAuthorize("hasAnyRole('ADMIN','HR_MANAGER')")
    @GetMapping("/payroll")
    public List<PayrollResponse> payroll() {
        return reportService.payrollReport();
    }

    @PreAuthorize("hasAnyRole('ADMIN','HR_MANAGER')")
    @GetMapping("/leave")
    public List<LeaveResponse> leave() {
        return reportService.leaveReport();
    }

    @PreAuthorize("hasAnyRole('ADMIN','HR_MANAGER')")
    @GetMapping("/attendance")
    public List<AttendanceResponse> attendance() {
        return reportService.attendanceReport();
    }

    @PreAuthorize("hasAnyRole('ADMIN','HR_MANAGER')")
    @GetMapping("/performance")
    public List<PerformanceResponse> performance() {
        return reportService.performanceReport();
    }
}