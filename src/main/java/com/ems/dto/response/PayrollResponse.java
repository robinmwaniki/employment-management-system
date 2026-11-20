package com.ems.dto.response;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.YearMonth;

/** Payroll amounts and the statutory calculation snapshot for one pay period. */
@Data
@Builder
public class PayrollResponse {
    private Long id;
    private Long employeeId;
    private String employeeName;
    private YearMonth payrollMonth;
    private BigDecimal basicSalary;
    private BigDecimal houseAllowance;
    private BigDecimal transportAllowance;
    private BigDecimal bonus;
    private BigDecimal grossPay;
    private BigDecimal taxablePay;
    private BigDecimal nssf;
    private BigDecimal shif;
    private BigDecimal housingLevy;
    private BigDecimal employerNssf;
    private BigDecimal employerHousingLevy;
    private BigDecimal pensionContribution;
    private BigDecimal personalRelief;
    private BigDecimal insuranceRelief;
    private BigDecimal insurancePremium;
    private BigDecimal tax;
    private BigDecimal deductions;
    private BigDecimal netSalary;
    private boolean residentTaxpayer;
}