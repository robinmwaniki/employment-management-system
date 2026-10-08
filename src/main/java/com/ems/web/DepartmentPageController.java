package com.ems.web;

import com.ems.dto.request.DepartmentRequest;
import com.ems.dto.response.DepartmentResponse;
import com.ems.service.interfaces.DepartmentService;
import com.ems.service.interfaces.SystemLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
public class DepartmentPageController {

    private final DepartmentService departmentService;
    private final SystemLogService systemLogService;

    @GetMapping("/departments")
    public String departments(Model model) {

        model.addAttribute(
                "departments",
                departmentService.getAllDepartments());

        systemLogService.saveLog(
                "Administrator",
                "ADMIN",
                "VIEW DEPARTMENTS",
                "Viewed department list");

        return "departments";
    }

    @GetMapping("/departments/new")
    public String newDepartment(Model model) {

        model.addAttribute(
                "department",
                new DepartmentRequest());

        systemLogService.saveLog(
                "Administrator",
                "ADMIN",
                "OPEN CREATE DEPARTMENT",
                "Opened create department form");

        return "department-form";
    }

    @PostMapping("/departments/save")
    public String saveDepartment(
            @ModelAttribute DepartmentRequest request) {

        departmentService.createDepartment(request);

        return "redirect:/departments";
    }

    @GetMapping("/departments/edit/{id}")
    public String editDepartment(
            @PathVariable Long id,
            Model model) {

        DepartmentResponse department =
                departmentService.getDepartmentById(id);

        DepartmentRequest request =
                new DepartmentRequest();

        request.setName(department.getName());
        request.setCode(department.getCode());

        model.addAttribute(
                "department",
                request);

        model.addAttribute(
                "departmentId",
                id);

        systemLogService.saveLog(
                "Administrator",
                "ADMIN",
                "OPEN EDIT DEPARTMENT",
                "Opened edit form for department "
                        + department.getName());

        return "department-form";
    }

    @PostMapping("/departments/update/{id}")
    public String updateDepartment(
            @PathVariable Long id,
            @ModelAttribute DepartmentRequest request) {

        departmentService.updateDepartment(id, request);

        return "redirect:/departments";
    }

    @PostMapping("/departments/delete/{id}")
    public String deleteDepartment(
            @PathVariable Long id) {

        departmentService.deleteDepartment(id);

        return "redirect:/departments";
    }

}