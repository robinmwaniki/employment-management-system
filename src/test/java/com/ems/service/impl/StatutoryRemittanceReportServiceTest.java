package com.ems.service.impl;

import com.ems.entity.CompanyProfile;
import com.ems.entity.Employee;
import com.ems.entity.Payroll;
import com.ems.repository.CompanyProfileRepository;
import com.ems.repository.PayrollRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatutoryRemittanceReportServiceTest {
    @Mock PayrollRepository payrollRepository;
    @Mock CompanyProfileRepository companyProfileRepository;
    @InjectMocks StatutoryRemittanceReportService service;

    @Test
    void aggregatesMonthlyObligationsAndFlagsMissingStatutoryIdentifiers() {
        YearMonth month = YearMonth.of(2026, 10);
        Employee employee = Employee.builder().id(7L).firstName("Jane").lastName("Doe")
                .kraPin("A123456789Z").nssfNumber("NSSF-123").shaNumber(null).build();
        Payroll payroll = Payroll.builder().id(20L).employee(employee).payrollMonth(month)
                .grossPay(new BigDecimal("80000.00")).tax(new BigDecimal("9000.00"))
                .nssf(new BigDecimal("4800.00")).employerNssf(new BigDecimal("4800.00"))
                .shif(new BigDecimal("2200.00")).housingLevy(new BigDecimal("1200.00"))
                .employerHousingLevy(new BigDecimal("1200.00")).build();
        when(payrollRepository.findByPayrollMonth(month)).thenReturn(List.of(payroll));
        when(companyProfileRepository.findById(1)).thenReturn(Optional.of(
                CompanyProfile.builder().id(1).companyName("Example Limited")
                        .kraPin("P123456789Q").nssfEmployerNumber("ER-1").shaEmployerNumber("SHA-1").build()));

        var report = service.generate(month);

        assertEquals(1, report.employeeCount());
        assertEquals(0, report.missingKraPinCount());
        assertEquals(0, report.missingNssfNumberCount());
        assertEquals(1, report.missingShaNumberCount());
        assertEquals(new BigDecimal("9000.00"), report.payeTotal());
        assertEquals(new BigDecimal("4800.00"), report.employeeNssfTotal());
        assertEquals(new BigDecimal("4800.00"), report.employerNssfTotal());
        assertEquals(new BigDecimal("2200.00"), report.shifTotal());
        assertEquals(new BigDecimal("1200.00"), report.employeeHousingLevyTotal());
        assertEquals(new BigDecimal("1200.00"), report.employerHousingLevyTotal());
        assertEquals(new BigDecimal("23200.00"), report.totalRemittance());
        assertEquals("Example Limited", report.company().companyName());
        assertEquals("2026-11-09", report.obligations().get(0).dueDate().toString());
        assertEquals("2026-11-13", report.obligations().get(3).dueDate().toString());
    }

    @Test
    void returnsZeroTotalsForMonthWithoutPayroll() {
        YearMonth month = YearMonth.of(2026, 11);
        when(payrollRepository.findByPayrollMonth(month)).thenReturn(List.of());
        when(companyProfileRepository.findById(1)).thenReturn(Optional.of(
                CompanyProfile.builder().id(1).companyName("Example Limited").build()));

        var report = service.generate(month);

        assertEquals(0, report.employeeCount());
        assertEquals(new BigDecimal("0.00"), report.totalRemittance());
        assertEquals(0, report.employees().size());
    }
}
