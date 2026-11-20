package com.ems.mapper;

import com.ems.dto.response.PayrollResponse;
import com.ems.entity.Payroll;

/** Converts persisted payroll records into API response DTOs. */
public final class PayrollMapper {
    private PayrollMapper() {}
    public static PayrollResponse toResponse(Payroll p) {
        return PayrollResponse.builder()
            .id(p.getId()).employeeId(p.getEmployee().getId())
            .employeeName(p.getEmployee().getFirstName()+" "+p.getEmployee().getLastName())
            .payrollMonth(p.getPayrollMonth()).basicSalary(p.getBasicSalary())
            .houseAllowance(p.getHouseAllowance()).transportAllowance(p.getTransportAllowance())
            .bonus(p.getBonus()).grossPay(p.getGrossPay()).taxablePay(p.getTaxablePay())
            .nssf(p.getNssf()).shif(p.getShif()).housingLevy(p.getHousingLevy())
            .employerNssf(p.getEmployerNssf()).employerHousingLevy(p.getEmployerHousingLevy())
            .pensionContribution(p.getPensionContribution()).personalRelief(p.getPersonalRelief())
            .insuranceRelief(p.getInsuranceRelief()).tax(p.getTax()).deductions(p.getDeductions())
            .netSalary(p.getNetSalary()).residentTaxpayer(p.isResidentTaxpayer()).build();
    }
}