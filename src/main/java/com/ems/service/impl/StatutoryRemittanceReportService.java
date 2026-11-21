package com.ems.service.impl;

import com.ems.dto.response.CompanyProfileResponse;
import com.ems.dto.response.StatutoryRemittanceReportResponse;
import com.ems.entity.CompanyProfile;
import com.ems.entity.Payroll;
import com.ems.exception.ResourceNotFoundException;
import com.ems.repository.CompanyProfileRepository;
import com.ems.repository.PayrollRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatutoryRemittanceReportService {
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private final PayrollRepository payrollRepository;
    private final CompanyProfileRepository companyProfileRepository;

    public StatutoryRemittanceReportResponse generate(YearMonth month) {
        List<Payroll> payrolls = payrollRepository.findByPayrollMonth(month);
        CompanyProfile p = companyProfileRepository.findById(1).orElseThrow(() ->
                new ResourceNotFoundException("Company profile is not initialized. Verify database migrations."));

        BigDecimal gross = sum(payrolls, Payroll::getGrossPay);
        BigDecimal paye = sum(payrolls, Payroll::getTax);
        BigDecimal empNssf = sum(payrolls, Payroll::getNssf);
        BigDecimal erNssf = sum(payrolls, Payroll::getEmployerNssf);
        BigDecimal shif = sum(payrolls, Payroll::getShif);
        BigDecimal empHousing = sum(payrolls, Payroll::getHousingLevy);
        BigDecimal erHousing = sum(payrolls, Payroll::getEmployerHousingLevy);
        LocalDate ninth = month.plusMonths(1).atDay(9);
        LocalDate housingDue = ninthWorkingDayAfter(month.atEndOfMonth());

        List<StatutoryRemittanceReportResponse.Obligation> obligations = List.of(
            new StatutoryRemittanceReportResponse.Obligation("PAYE", "Pay As You Earn",
                paye, ZERO, paye, ninth, "On or before the 9th day of the following month", "KRA"),
            new StatutoryRemittanceReportResponse.Obligation("NSSF", "National Social Security Fund",
                empNssf, erNssf, empNssf.add(erNssf), ninth,
                "On or before the 9th day of the following month", "NSSF"),
            new StatutoryRemittanceReportResponse.Obligation("SHIF", "Social Health Insurance Fund",
                shif, ZERO, shif, ninth, "By the 9th day of the following month", "Social Health Authority"),
            new StatutoryRemittanceReportResponse.Obligation("HOUSING_LEVY", "Affordable Housing Levy",
                empHousing, erHousing, empHousing.add(erHousing), housingDue,
                "9th working day after month-end; verify gazetted public-holiday adjustments", "KRA"));

        List<StatutoryRemittanceReportResponse.EmployeeLine> lines = payrolls.stream().map(x ->
            new StatutoryRemittanceReportResponse.EmployeeLine(
                x.getEmployee().getId(),
                x.getEmployee().getFirstName() + " " + x.getEmployee().getLastName(),
                x.getEmployee().getKraPin(), x.getEmployee().getNssfNumber(), x.getEmployee().getShaNumber(),
                amount(x.getTax()), amount(x.getNssf()), amount(x.getEmployerNssf()),
                amount(x.getShif()), amount(x.getHousingLevy()), amount(x.getEmployerHousingLevy())
            )).toList();

        BigDecimal total = paye.add(empNssf).add(erNssf).add(shif)
                .add(empHousing).add(erHousing).setScale(2);
        CompanyProfileResponse company = new CompanyProfileResponse(p.getCompanyName(), p.getKraPin(),
                p.getNssfEmployerNumber(), p.getShaEmployerNumber(), p.getAddress(), p.getPhoneNumber(), p.getEmail());

        return new StatutoryRemittanceReportResponse(month, company, payrolls.size(),
                (int) payrolls.stream().filter(x -> blank(x.getEmployee().getKraPin())).count(),
                (int) payrolls.stream().filter(x -> blank(x.getEmployee().getNssfNumber())).count(),
                (int) payrolls.stream().filter(x -> blank(x.getEmployee().getShaNumber())).count(),
                gross, paye, empNssf, erNssf, shif, empHousing, erHousing, total, obligations, lines);
    }

    private static LocalDate ninthWorkingDayAfter(LocalDate monthEnd) {
        LocalDate date = monthEnd;
        int count = 0;
        while (count < 9) {
            date = date.plusDays(1);
            DayOfWeek day = date.getDayOfWeek();
            if (day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY) count++;
        }
        return date;
    }

    private static BigDecimal sum(List<Payroll> values, Function<Payroll, BigDecimal> getter) {
        return values.stream().map(x -> amount(getter.apply(x)))
                .reduce(ZERO, BigDecimal::add).setScale(2);
    }

    private static BigDecimal amount(BigDecimal value) {
        return value == null ? ZERO : value.setScale(2);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
