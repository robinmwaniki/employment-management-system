package com.ems.web;

import com.ems.dto.request.PerformanceRequest;
import com.ems.repository.EmployeeRepository;
import com.ems.service.interfaces.PerformanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
@RequestMapping("/performance")
public class PerformancePageController {

    private final PerformanceService performanceService;
    private final EmployeeRepository employeeRepository;

    @GetMapping
    public String performance(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model) {

        model.addAttribute(
                "reviews",
                performanceService.getAllReviews(page, size));

        return "performance";
    }

    @GetMapping("/new")
    public String newReview(Model model) {

        model.addAttribute(
                "review",
                new PerformanceRequest());

        model.addAttribute(
                "employees",
                employeeRepository.findAll());

        return "performance-form";
    }

    @PostMapping("/save")
    public String saveReview(
            @ModelAttribute PerformanceRequest request) {

        performanceService.createReview(request);

        return "redirect:/performance";
    }

    @GetMapping("/edit/{id}")
    public String editReview(
            @PathVariable Long id,
            Model model) {

        model.addAttribute(
                "review",
                performanceService.getReviewById(id));

        model.addAttribute(
                "employees",
                employeeRepository.findAll());

        return "performance-form";
    }

    @PostMapping("/update/{id}")
    public String updateReview(
            @PathVariable Long id,
            @ModelAttribute PerformanceRequest request) {

        performanceService.updateReview(id, request);

        return "redirect:/performance";
    }

    @GetMapping("/delete/{id}")
    public String deleteReview(
            @PathVariable Long id) {

        performanceService.deleteReview(id);

        return "redirect:/performance";
    }

}