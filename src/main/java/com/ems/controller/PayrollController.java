package com.ems.controller;

import com.ems.dto.request.PayrollRequest;
import com.ems.dto.response.PayrollResponse;
import com.ems.service.interfaces.PayrollService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payroll")
@RequiredArgsConstructor
public class PayrollController {

    private final PayrollService payrollService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PayrollResponse generatePayroll(
            @Valid @RequestBody PayrollRequest request) {

        return payrollService.generatePayroll(request);
    }

    @GetMapping("/{id}")
    public PayrollResponse getPayroll(
            @PathVariable Long id) {

        return payrollService.getPayroll(id);
    }

    @GetMapping("/employee/{employeeId}")
    public List<PayrollResponse> getEmployeePayrolls(
            @PathVariable Long employeeId) {

        return payrollService.getEmployeePayrolls(employeeId);
    }
}