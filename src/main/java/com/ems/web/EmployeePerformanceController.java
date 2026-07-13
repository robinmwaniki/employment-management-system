package com.ems.web;

import com.ems.repository.UserRepository;
import com.ems.service.interfaces.PerformanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class EmployeePerformanceController {

    private final UserRepository userRepository;
    private final PerformanceService performanceService;

    @GetMapping("/employee/performance")
    public String performance(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model) {

        String username = authentication.getName();

        var user = userRepository.findByUsername(username)
                .orElseThrow(() ->
                        new RuntimeException("User not found"));

        Long employeeId = user.getEmployee().getId();

        model.addAttribute(
                "reviews",
                performanceService.getEmployeeReviews(
                        employeeId,
                        page,
                        size));

        return "employee-performance";
    }

}