package com.ems.service.impl;

import com.ems.dto.request.PayrollRequest;
import com.ems.dto.response.PayrollResponse;
import com.ems.entity.Employee;
import com.ems.entity.Payroll;
import com.ems.exception.ResourceNotFoundException;
import com.ems.mapper.PayrollMapper;
import com.ems.repository.EmployeeRepository;
import com.ems.repository.PayrollRepository;
import com.ems.service.interfaces.PayrollService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
@Service
@RequiredArgsConstructor
public class PayrollServiceImpl implements PayrollService {

    private final PayrollRepository payrollRepository;
    private final EmployeeRepository employeeRepository;

    @Override
    public PayrollResponse generatePayroll(PayrollRequest request) {

        Employee employee = employeeRepository.findById(request.getEmployeeId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Employee not found with id: " + request.getEmployeeId()));

        payrollRepository.findByEmployeeAndPayrollMonth(
                employee,
                request.getPayrollMonth()
        ).ifPresent(p -> {
            throw new IllegalArgumentException(
                    "Payroll has already been generated for this employee and month."
            );
        });

        BigDecimal basicSalary = employee.getSalary();

        BigDecimal netSalary = basicSalary
                .add(request.getHouseAllowance())
                .add(request.getTransportAllowance())
                .add(request.getBonus())
                .subtract(request.getDeductions())
                .subtract(request.getTax());

        Payroll payroll = Payroll.builder()
                .employee(employee)
                .payrollMonth(request.getPayrollMonth())
                .basicSalary(basicSalary)
                .houseAllowance(request.getHouseAllowance())
                .transportAllowance(request.getTransportAllowance())
                .bonus(request.getBonus())
                .deductions(request.getDeductions())
                .tax(request.getTax())
                .netSalary(netSalary)
                .build();

        Payroll savedPayroll = payrollRepository.save(payroll);

        return PayrollMapper.toResponse(savedPayroll);
    }

    @Override
    public PayrollResponse getPayroll(Long id) {

        Payroll payroll = payrollRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payroll not found with id: " + id));

        return PayrollMapper.toResponse(payroll);
    }

    @Override
    public List<PayrollResponse> getEmployeePayrolls(Long employeeId) {

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Employee not found with id: " + employeeId));

        return payrollRepository.findByEmployee(employee)
                .stream()
                .map(PayrollMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public Page<PayrollResponse> getAllPayrolls(int page, int size) {

        Pageable pageable = PageRequest.of(page, size);

        return payrollRepository.findAll(pageable)
                .map(PayrollMapper::toResponse);
    }

    @Override
    public PayrollResponse updatePayroll(Long id, PayrollRequest request) {

        Payroll payroll = payrollRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Payroll not found"));

        Employee employee = employeeRepository.findById(request.getEmployeeId())
                .orElseThrow(() ->
                        new ResourceNotFoundException("Employee not found"));

        BigDecimal basicSalary = employee.getSalary();

        payroll.setEmployee(employee);
        payroll.setPayrollMonth(request.getPayrollMonth());
        payroll.setBasicSalary(basicSalary);
        payroll.setHouseAllowance(request.getHouseAllowance());
        payroll.setTransportAllowance(request.getTransportAllowance());
        payroll.setBonus(request.getBonus());
        payroll.setDeductions(request.getDeductions());
        payroll.setTax(request.getTax());

        BigDecimal netSalary = basicSalary
                .add(request.getHouseAllowance())
                .add(request.getTransportAllowance())
                .add(request.getBonus())
                .subtract(request.getDeductions())
                .subtract(request.getTax());

        payroll.setNetSalary(netSalary);

        Payroll updatedPayroll = payrollRepository.save(payroll);

        return PayrollMapper.toResponse(updatedPayroll);
    }
    @Override
    public void deletePayroll(Long id){

        Payroll payroll = payrollRepository.findById(id)
                .orElseThrow(()->
                        new ResourceNotFoundException(
                                "Payroll not found"));

        payrollRepository.delete(payroll);
    }
}