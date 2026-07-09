package com.ems.web;

import com.ems.dto.request.UserRequest;
import com.ems.dto.response.UserResponse;
import com.ems.repository.EmployeeRepository;
import com.ems.service.interfaces.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
public class UserPageController {

    private final UserService userService;
    private final EmployeeRepository employeeRepository;

    @GetMapping("/users")
    public String users(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model) {

        model.addAttribute("users",
                userService.getAllUsers(page, size));

        return "users";
    }

    @GetMapping("/users/new")
    public String newUser(Model model) {

        model.addAttribute("user", new UserRequest());

        model.addAttribute("employees",
                employeeRepository.findAll());

        return "user-form";
    }

    @PostMapping("/users/save")
    public String saveUser(
            @ModelAttribute UserRequest request) {

        userService.createUser(request);

        return "redirect:/users";
    }

    @GetMapping("/users/edit/{id}")
    public String editUser(
            @PathVariable Long id,
            Model model) {

        UserResponse response = userService.getUser(id);

        UserRequest request = new UserRequest();

        request.setEmployeeId(response.getEmployeeId());
        request.setUsername(response.getUsername());
        request.setRole(response.getRole());

        model.addAttribute("user", request);
        model.addAttribute("userId", id);

        model.addAttribute("employees",
                employeeRepository.findAll());

        return "user-form";
    }

    @PostMapping("/users/update/{id}")
    public String updateUser(
            @PathVariable Long id,
            @ModelAttribute UserRequest request) {

        userService.updateUser(id, request);

        return "redirect:/users";
    }

    @GetMapping("/users/delete/{id}")
    public String deleteUser(
            @PathVariable Long id) {

        userService.deleteUser(id);

        return "redirect:/users";
    }

}