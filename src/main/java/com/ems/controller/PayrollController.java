package com.ems.controller;

import com.ems.dto.request.PayrollRequest;
import com.ems.dto.response.PayrollResponse;
import com.ems.service.interfaces.PayrollService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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

    @GetMapping
    public Page<PayrollResponse> getAllPayroll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        return payrollService.getAllPayrolls(page, size);
    }

    @GetMapping("/{id}")
    public PayrollResponse getPayroll(
            @PathVariable Long id) {

        return payrollService.getPayroll(id);
    }

    @PutMapping("/{id}")
    public PayrollResponse updatePayroll(
            @PathVariable Long id,
            @Valid @RequestBody PayrollRequest request) {

        return payrollService.updatePayroll(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePayroll(@PathVariable Long id) {

        payrollService.deletePayroll(id);
    }

    @GetMapping("/employee/{employeeId}")
    public List<PayrollResponse> getEmployeePayrolls(
            @PathVariable Long employeeId) {

        return payrollService.getEmployeePayrolls(employeeId);
    }
}
