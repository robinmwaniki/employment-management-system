package com.ems.web;

import com.ems.service.interfaces.AttendanceService;
import com.ems.service.interfaces.DepartmentService;
import com.ems.service.interfaces.EmployeeService;
import com.ems.service.interfaces.LeaveService;
import com.ems.service.interfaces.PayrollService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class ReportsPageController {

    private final EmployeeService employeeService;
    private final AttendanceService attendanceService;
    private final LeaveService leaveService;
    private final PayrollService payrollService;
    private final DepartmentService departmentService;

    @GetMapping("/reports")
    public String reports(Model model) {

        model.addAttribute(
                "employeeCount",
                employeeService.getAllEmployees(0, 1).getTotalElements());

        model.addAttribute(
                "attendanceCount",
                attendanceService.getAllAttendance(0, 1).getTotalElements());

        model.addAttribute(
                "leaveCount",
                leaveService.getAllLeaves(0, 1).getTotalElements());

        model.addAttribute(
                "payrollCount",
                payrollService.getAllPayrolls(0, 1).getTotalElements());

        model.addAttribute(
                "departmentCount",
                departmentService.getAllDepartments().size());

        return "reports";
    }

    @GetMapping("/reports/employees")
    public String employeeReport(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model) {

        model.addAttribute(
                "employees",
                employeeService.getAllEmployees(page, size));

        return "employee-report";
    }

    @GetMapping("/reports/attendance")
    public String attendanceReport(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model) {

        model.addAttribute(
                "attendance",
                attendanceService.getAllAttendance(page, size));

        return "attendance-report";
    }

    @GetMapping("/reports/leaves")
    public String leaveReport(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model) {

        model.addAttribute(
                "leaves",
                leaveService.getAllLeaves(page, size));

        return "leave-report";
    }

    @GetMapping("/reports/payroll")
    public String payrollReport(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model) {

        model.addAttribute(
                "payrolls",
                payrollService.getAllPayrolls(page, size));

        return "payroll-report";
    }

    @GetMapping("/reports/departments")
    public String departmentReport(Model model) {

        model.addAttribute(
                "departments",
                departmentService.getAllDepartments());

        return "department-report";
    }

}