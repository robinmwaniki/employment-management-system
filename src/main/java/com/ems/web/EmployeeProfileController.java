package com.ems.web;

import com.ems.dto.request.EmployeeRequest;
import com.ems.dto.response.EmployeeResponse;
import com.ems.repository.UserRepository;
import com.ems.service.interfaces.EmployeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class EmployeeProfileController {

    private final UserRepository userRepository;
    private final EmployeeService employeeService;
    private final com.ems.security.service.UserService userService;

    @GetMapping("/employee/profile")
    public String profile(Authentication authentication, Model model) {

        String username = authentication.getName();

        Long employeeId = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"))
                .getEmployee()
                .getId();

        EmployeeResponse employee =
                employeeService.getEmployeeById(employeeId);

        model.addAttribute("employee", employee);

        return "employee-profile";
    }

    @GetMapping("/employee/profile/edit")
    public String editProfile(Authentication authentication, Model model) {

        String username = authentication.getName();

        var user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Long employeeId = user.getEmployee().getId();

        EmployeeResponse employee =
                employeeService.getEmployeeById(employeeId);

        EmployeeRequest request = new EmployeeRequest();

        request.setFirstName(employee.getFirstName());
        request.setLastName(employee.getLastName());
        request.setEmail(employee.getEmail());
        request.setPhoneNumber(employee.getPhoneNumber());
        request.setDepartmentId(employee.getDepartmentId());

        model.addAttribute("employee", request);
        model.addAttribute("employeeId", employeeId);

        return "employee-profile-form";
    }

    @PostMapping("/employee/profile/update")
    public String updateProfile(
            Authentication authentication,
            @ModelAttribute EmployeeRequest request) {

        String username = authentication.getName();

        var user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Long employeeId = user.getEmployee().getId();

        employeeService.updateEmployee(employeeId, request);

        return "redirect:/employee/profile";
    }
    @GetMapping("/employee/change-password")
    public String changePasswordPage() {
        return "change-password";
    }
    @PostMapping("/employee/change-password")
    public String changePassword(
            Authentication authentication,
            @RequestParam String currentPassword,
            @RequestParam String newPassword,
            @RequestParam String confirmPassword,
            RedirectAttributes redirectAttributes) {

        System.out.println("===== CHANGE PASSWORD CONTROLLER =====");

        if (!newPassword.equals(confirmPassword)) {

            redirectAttributes.addFlashAttribute(
                    "error",
                    "Passwords do not match.");

            return "redirect:/employee/change-password";
        }

        userService.changePassword(
                authentication.getName(),
                currentPassword,
                newPassword);

        redirectAttributes.addFlashAttribute(
                "success",
                "Password updated successfully.");

        return "redirect:/employee/profile";
    }
}