package com.ems.mapper;

import com.ems.dto.response.PayrollResponse;
import com.ems.entity.Payroll;

public class PayrollMapper {

    private PayrollMapper() {
    }

    public static PayrollResponse toResponse(Payroll payroll) {

        return PayrollResponse.builder()
                .id(payroll.getId())
                .employeeId(payroll.getEmployee().getId())
                .employeeName(
                        payroll.getEmployee().getFirstName()
                                + " "
                                + payroll.getEmployee().getLastName()
                )
                .payrollMonth(payroll.getPayrollMonth())
                .basicSalary(payroll.getBasicSalary())
                .houseAllowance(payroll.getHouseAllowance())
                .transportAllowance(payroll.getTransportAllowance())
                .bonus(payroll.getBonus())
                .deductions(payroll.getDeductions())
                .tax(payroll.getTax())
                .netSalary(payroll.getNetSalary())
                .build();
    }

}