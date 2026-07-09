package com.ems.web;

import com.ems.dto.request.LeaveRequest;
import com.ems.service.interfaces.EmployeeService;
import com.ems.service.interfaces.LeaveService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
public class LeavePageController {

    private final LeaveService leaveService;
    private final EmployeeService employeeService;

    @GetMapping("/leave")
    public String leavePage(
            @RequestParam(defaultValue = "0") int page,
            Model model) {

        model.addAttribute(
                "leaves",
                leaveService.getAllLeaves(page, 10));

        return "leave-list";
    }

    @GetMapping("/leave/new")
    public String newLeave(Model model) {

        model.addAttribute(
                "leave",
                new LeaveRequest());

        model.addAttribute(
                "employees",
                employeeService.getAllEmployees(0, 100).getContent());

        return "leave-form";
    }

    @PostMapping("/leave/save")
    public String saveLeave(
            @ModelAttribute LeaveRequest request) {

        leaveService.applyLeave(request);

        return "redirect:/leave";
    }

    @GetMapping("/leave/approve/{id}")
    public String approveLeave(
            @PathVariable Long id) {

        leaveService.approveLeave(id);

        return "redirect:/leave";
    }

    @GetMapping("/leave/reject/{id}")
    public String rejectLeave(
            @PathVariable Long id) {

        leaveService.rejectLeave(id);

        return "redirect:/leave";
    }

    @GetMapping("/leave/cancel/{id}")
    public String cancelLeave(
            @PathVariable Long id) {

        leaveService.cancelLeave(id);

        return "redirect:/leave";
    }
}