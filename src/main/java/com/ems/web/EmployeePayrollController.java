package com.ems.web;

import com.ems.entity.User;
import com.ems.repository.UserRepository;
import com.ems.service.interfaces.PayrollService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;

@Controller
@RequiredArgsConstructor
public class EmployeePayrollController {

    private final PayrollService payrollService;
    private final UserRepository userRepository;

    @GetMapping("/employee/payroll")
    public String payroll(
            Authentication authentication,
            Model model) {

        User user = userRepository
                .findByUsername(authentication.getName())
                .orElseThrow(() ->
                        new RuntimeException("User not found"));

        model.addAttribute(
                "payrolls",
                payrollService.getEmployeePayrolls(
                        user.getEmployee().getId()));

        return "employee-payroll";
    }

    @GetMapping("/employee/payroll/{id}")
    public String payrollDetails(
            @PathVariable Long id,
            Authentication authentication,
            Model model) {

        User user = userRepository
                .findByUsername(authentication.getName())
                .orElseThrow(() ->
                        new RuntimeException("User not found"));

        model.addAttribute(
                "payroll",
                payrollService.getEmployeePayroll(
                        user.getEmployee().getId(),
                        id));

        return "employee-payroll-view";
    }

}