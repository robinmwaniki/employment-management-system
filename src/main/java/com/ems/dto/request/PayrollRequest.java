package com.ems.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.YearMonth;

@Data
public class PayrollRequest {

    @NotNull
    private Long employeeId;

    @NotNull
    private YearMonth payrollMonth;

    @DecimalMin("0.0")
    private BigDecimal houseAllowance;

    @DecimalMin("0.0")
    private BigDecimal transportAllowance;

    @DecimalMin("0.0")
    private BigDecimal bonus;

    @DecimalMin("0.0")
    private BigDecimal deductions;

    @DecimalMin("0.0")
    private BigDecimal tax;

}