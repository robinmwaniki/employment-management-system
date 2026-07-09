package com.ems.web;

import com.ems.dto.request.DepartmentRequest;
import com.ems.dto.response.DepartmentResponse;
import com.ems.service.interfaces.DepartmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
public class DepartmentPageController {

    private final DepartmentService departmentService;

    @GetMapping("/departments")
    public String departments(Model model){

        model.addAttribute(
                "departments",
                departmentService.getAllDepartments());

        return "departments";
    }

    @GetMapping("/departments/new")
    public String newDepartment(Model model){

        model.addAttribute(
                "department",
                new DepartmentRequest());

        return "department-form";
    }

    @PostMapping("/departments/save")
    public String saveDepartment(
            @ModelAttribute DepartmentRequest request){

        departmentService.createDepartment(request);

        return "redirect:/departments";
    }

    @GetMapping("/departments/edit/{id}")
    public String editDepartment(
            @PathVariable Long id,
            Model model){

        DepartmentResponse department =
                departmentService.getDepartmentById(id);

        DepartmentRequest request =
                new DepartmentRequest();

        request.setName(department.getName());


        model.addAttribute("department", request);
        model.addAttribute("departmentId", id);

        return "department-form";
    }

    @PostMapping("/departments/update/{id}")
    public String updateDepartment(
            @PathVariable Long id,
            @ModelAttribute DepartmentRequest request){

        departmentService.updateDepartment(id, request);

        return "redirect:/departments";
    }

    @GetMapping("/departments/delete/{id}")
    public String deleteDepartment(
            @PathVariable Long id){

        departmentService.deleteDepartment(id);

        return "redirect:/departments";
    }

}