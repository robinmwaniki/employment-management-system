package com.ems.web;

import com.ems.dto.request.LeaveRequest;
import com.ems.exception.ResourceNotFoundException;
import com.ems.repository.UserRepository;
import com.ems.service.interfaces.LeaveService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Leave self-service for the signed-in employee. The employee is always taken
 * from the authenticated account, never from the request.
 */
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

        model.addAttribute(
                "leaves",
                leaveService.getEmployeeLeaves(
                        currentEmployeeId(authentication),
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

        request.setEmployeeId(currentEmployeeId(authentication));

        leaveService.applyLeave(request);

        return "redirect:/employee/leave";
    }

    @PostMapping("/employee/leave/cancel/{id}")
    public String cancelLeave(

            Authentication authentication,

            @PathVariable Long id) {

        leaveService.cancelOwnLeave(id, currentEmployeeId(authentication));

        return "redirect:/employee/leave";
    }

    /**
     * Resolves the employee profile linked to the signed-in account.
     */
    private Long currentEmployeeId(Authentication authentication) {

        var user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() ->
                        new ResourceNotFoundException("User not found"));

        if (user.getEmployee() == null) {
            throw new ResourceNotFoundException(
                    "No employee profile is linked to this account.");
        }

        return user.getEmployee().getId();
    }
}