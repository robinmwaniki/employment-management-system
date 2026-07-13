package com.ems.web;

import com.ems.dto.request.LeaveRequest;
import com.ems.repository.UserRepository;
import com.ems.service.interfaces.LeaveService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class EmployeeLeaveController {

    private final LeaveService leaveService;
    private final UserRepository userRepository;

    @GetMapping("/employee/leave")
    public String employeeLeave(

            Authentication authentication,

            @RequestParam(defaultValue = "0") int page,

            Model model) {

        String username = authentication.getName();

        var user = userRepository.findByUsername(username)
                .orElseThrow(() ->
                        new RuntimeException("User not found"));

        Long employeeId = user.getEmployee().getId();

        model.addAttribute(
                "leaves",
                leaveService.getEmployeeLeaves(
                        employeeId,
                        page,
                        10));

        return "employee-leave";
    }
    @GetMapping("/employee/leave/apply")
    public String applyLeaveForm(Model model) {

        model.addAttribute(
                "leave",
                new LeaveRequest());

        return "employee-leave-form";
    }

    @PostMapping("/employee/leave/save")
    public String saveLeave(

            Authentication authentication,

            @ModelAttribute LeaveRequest request) {

        String username = authentication.getName();

        var user = userRepository.findByUsername(username)
                .orElseThrow(() ->
                        new RuntimeException("User not found"));

        request.setEmployeeId(
                user.getEmployee().getId());

        leaveService.applyLeave(request);

        return "redirect:/employee/leave";
    }

}