package com.ems.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public record StatutoryRemittanceReportResponse(
        YearMonth payrollMonth,
        CompanyProfileResponse company,
        int employeeCount,
        int missingKraPinCount,
        int missingNssfNumberCount,
        int missingShaNumberCount,
        BigDecimal grossPayTotal,
        BigDecimal payeTotal,
        BigDecimal employeeNssfTotal,
        BigDecimal employerNssfTotal,
        BigDecimal shifTotal,
        BigDecimal employeeHousingLevyTotal,
        BigDecimal employerHousingLevyTotal,
        BigDecimal totalRemittance,
        List<Obligation> obligations,
        List<EmployeeLine> employees) {

    public record Obligation(String code, String name, BigDecimal employeeAmount,
            BigDecimal employerAmount, BigDecimal totalAmount, LocalDate dueDate,
            String dueDateRule, String remittingAuthority) {}

    public record EmployeeLine(Long employeeId, String employeeName, String kraPin,
            String nssfNumber, String shaNumber, BigDecimal paye, BigDecimal employeeNssf,
            BigDecimal employerNssf, BigDecimal shif, BigDecimal employeeHousingLevy,
            BigDecimal employerHousingLevy) {}
}
