package com.ems.web;

import com.ems.service.interfaces.EmployeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import com.ems.dto.response.EmployeeResponse;
import com.ems.dto.request.EmployeeRequest;
import com.ems.repository.DepartmentRepository;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.RequestParam;
@Controller
@RequiredArgsConstructor
public class EmployeePageController {

    private final EmployeeService employeeService;
    private final DepartmentRepository departmentRepository;

    @GetMapping("/employees")
    public String employees(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            Model model) {

        Page<EmployeeResponse> employees;

        if (keyword != null && !keyword.isBlank()) {

            employees = employeeService.searchEmployees(keyword, page, size);

        } else {

            employees = employeeService.getAllEmployees(page, size);

        }

        model.addAttribute("employees", employees);
        model.addAttribute("keyword", keyword);

        return "employees";
    }
    @GetMapping("/employees/new")
    public String newEmployee(Model model){

        model.addAttribute("employee", new EmployeeRequest());

        model.addAttribute(
                "departments",
                departmentRepository.findAll()
        );

        return "employee-form";
    }
    @PostMapping("/employees/save")
    public String saveEmployee(@ModelAttribute("employee") EmployeeRequest request) {

        employeeService.createEmployee(request);

        return "redirect:/employees";
    }
    @GetMapping("/employees/edit/{id}")
    public String editEmployee(@PathVariable Long id, Model model) {

        EmployeeResponse employee = employeeService.getEmployeeById(id);

        EmployeeRequest request = new EmployeeRequest();

        request.setFirstName(employee.getFirstName());
        request.setLastName(employee.getLastName());
        request.setEmail(employee.getEmail());
        request.setPhoneNumber(employee.getPhoneNumber());
        request.setSalary(employee.getSalary());
        request.setHireDate(employee.getHireDate());
        request.setDepartmentId(employee.getDepartmentId());

        model.addAttribute("employee", request);
        model.addAttribute("employeeId", id);
        model.addAttribute("departments", departmentRepository.findAll());

        return "employee-form";
    }
    @PostMapping("/employees/update/{id}")
    public String updateEmployee(
            @PathVariable Long id,
            @ModelAttribute("employee") EmployeeRequest request) {

        employeeService.updateEmployee(id, request);

        return "redirect:/employees";
    }
    @PostMapping("/employees/delete/{id}")
    public String deleteEmployee(@PathVariable Long id) {

        employeeService.deleteEmployee(id);

        return "redirect:/employees";
    }
    @GetMapping("/employees/photo/{id}")
    public String uploadPhotoPage(
            @PathVariable Long id,
            Model model){

        model.addAttribute("employeeId", id);

        return "upload-photo";
    }
    @PostMapping("/employees/photo/{id}")
    public String uploadPhoto(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file){

        employeeService.uploadEmployeePhoto(id, file);

        return "redirect:/employees";
    }

}