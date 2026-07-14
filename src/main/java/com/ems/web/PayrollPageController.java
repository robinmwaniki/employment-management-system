package com.ems.web;

import com.ems.dto.request.PayrollRequest;
import com.ems.dto.response.PayrollResponse;
import com.ems.repository.EmployeeRepository;
import com.ems.service.interfaces.PayrollService;
import com.ems.service.interfaces.SystemLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
public class PayrollPageController {

    private final PayrollService payrollService;
    private final EmployeeRepository employeeRepository;
    private final SystemLogService systemLogService;

    @GetMapping("/payroll")
    public String payroll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model) {

        model.addAttribute(
                "payrolls",
                payrollService.getAllPayrolls(page, size));

        systemLogService.saveLog(
                "HR Admin",
                "ADMIN",
                "VIEW PAYROLL",
                "Viewed payroll records");

        return "payroll";
    }

    @GetMapping("/payroll/new")
    public String newPayroll(Model model) {

        model.addAttribute("payroll", new PayrollRequest());

        model.addAttribute(
                "employees",
                employeeRepository.findAll());

        systemLogService.saveLog(
                "HR Admin",
                "ADMIN",
                "OPEN PAYROLL FORM",
                "Opened payroll creation form");

        return "payroll-form";
    }

    @PostMapping("/payroll/save")
    public String savePayroll(
            @ModelAttribute PayrollRequest request) {

        payrollService.generatePayroll(request);

        return "redirect:/payroll";
    }

    @GetMapping("/payroll/edit/{id}")
    public String editPayroll(
            @PathVariable Long id,
            Model model) {

        PayrollResponse payroll = payrollService.getPayroll(id);

        PayrollRequest request = new PayrollRequest();

        request.setEmployeeId(payroll.getEmployeeId());
        request.setPayrollMonth(payroll.getPayrollMonth());
        request.setHouseAllowance(payroll.getHouseAllowance());
        request.setTransportAllowance(payroll.getTransportAllowance());
        request.setBonus(payroll.getBonus());
        request.setDeductions(payroll.getDeductions());
        request.setTax(payroll.getTax());

        model.addAttribute("payroll", request);
        model.addAttribute("payrollId", id);
        model.addAttribute("employees", employeeRepository.findAll());

        systemLogService.saveLog(
                "HR Admin",
                "ADMIN",
                "EDIT PAYROLL",
                "Opened payroll for editing");

        return "payroll-form";
    }

    @PostMapping("/payroll/update/{id}")
    public String updatePayroll(
            @PathVariable Long id,
            @ModelAttribute PayrollRequest request) {

        payrollService.updatePayroll(id, request);

        return "redirect:/payroll";
    }

    @GetMapping("/payroll/view/{id}")
    public String viewPayroll(
            @PathVariable Long id,
            Model model) {

        model.addAttribute(
                "payroll",
                payrollService.getPayroll(id));

        systemLogService.saveLog(
                "HR Admin",
                "ADMIN",
                "VIEW PAYSLIP",
                "Viewed payroll details");

        return "payroll-view";
    }

    @GetMapping("/payroll/delete/{id}")
    public String deletePayroll(
            @PathVariable Long id) {

        payrollService.deletePayroll(id);

        return "redirect:/payroll";
    }

}