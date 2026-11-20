package com.ems.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;
import java.time.YearMonth;

/** Input used to generate or update a monthly payroll record. */
@Data
public class PayrollRequest {
    @NotNull private Long employeeId;
    @NotNull private YearMonth payrollMonth;
    @DecimalMin("0.0") private BigDecimal houseAllowance = BigDecimal.ZERO;
    @DecimalMin("0.0") private BigDecimal transportAllowance = BigDecimal.ZERO;
    @DecimalMin("0.0") private BigDecimal bonus = BigDecimal.ZERO;
    @DecimalMin("0.0") private BigDecimal deductions = BigDecimal.ZERO;
    @DecimalMin("0.0") private BigDecimal pensionContribution = BigDecimal.ZERO;
    @DecimalMin("0.0") private BigDecimal insurancePremium = BigDecimal.ZERO;
    private boolean residentTaxpayer = true;
}