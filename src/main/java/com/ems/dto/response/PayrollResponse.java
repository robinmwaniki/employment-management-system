package com.ems.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.YearMonth;

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

    private BigDecimal deductions;

    private BigDecimal tax;

    private BigDecimal netSalary;

}