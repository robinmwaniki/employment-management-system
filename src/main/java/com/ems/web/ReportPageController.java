package com.ems.web;

import com.ems.service.interfaces.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class ReportPageController {

    private final ReportService reportService;

    @GetMapping("/reports")
    public String reports(Model model){

        model.addAttribute(
                "employees",
                reportService.employeeReport());

        model.addAttribute(
                "attendance",
                reportService.attendanceReport());

        model.addAttribute(
                "leaves",
                reportService.leaveReport());

        model.addAttribute(
                "payrolls",
                reportService.payrollReport());

        model.addAttribute(
                "performance",
                reportService.performanceReport());

        return "reports";
    }

}