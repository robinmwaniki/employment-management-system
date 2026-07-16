package com.ems.web;

import com.ems.dto.request.EmployeeRequest;
import com.ems.dto.response.EmployeeResponse;
import com.ems.entity.User;
import com.ems.repository.UserRepository;
import com.ems.service.interfaces.DepartmentService;
import com.ems.service.interfaces.EmployeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
public class EmployeeProfileController {

    private final UserRepository userRepository;
    private final EmployeeService employeeService;
    private final DepartmentService departmentService;

    @GetMapping("/employee/profile")
    public String profile(
            Authentication authentication,
            Model model) {

        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() ->
                        new RuntimeException("User not found"));

        EmployeeResponse employee =
                employeeService.getEmployeeById(
                        user.getEmployee().getId());

        model.addAttribute("employee", employee);

        return "employee-profile";
    }

    @GetMapping("/employee/profile/edit")
    public String editProfile(
            Authentication authentication,
            Model model) {

        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() ->
                        new RuntimeException("User not found"));

        EmployeeResponse employee =
                employeeService.getEmployeeById(
                        user.getEmployee().getId());

        EmployeeRequest request = new EmployeeRequest();

        request.setFirstName(employee.getFirstName());
        request.setLastName(employee.getLastName());
        request.setEmail(employee.getEmail());
        request.setPhoneNumber(employee.getPhoneNumber());
        request.setDepartmentId(employee.getDepartmentId());
        request.setHireDate(employee.getHireDate());
        request.setSalary(employee.getSalary());

        model.addAttribute("employee", request);
        model.addAttribute("departments",
                departmentService.getAllDepartments());
        model.addAttribute("employeeId",
                employee.getId());

        return "employee-profile-form";
    }

    @PostMapping("/employee/profile/update")
    public String updateProfile(
            Authentication authentication,
            @ModelAttribute EmployeeRequest request) {

        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() ->
                        new RuntimeException("User not found"));

        employeeService.updateEmployee(
                user.getEmployee().getId(),
                request);

        return "redirect:/employee/profile";
    }

}