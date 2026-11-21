package com.ems.service.impl;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KenyanPayrollCalculatorTest {
    private final KenyanPayrollCalculator calculator = new KenyanPayrollCalculator();

    @Test
    void calculatesKenyanStatutoryDeductionsForFebruary2026() {
        var result = calculator.calculate(YearMonth.of(2026, 2),
            new BigDecimal("50000"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, true);
        assertEquals(new BigDecimal("3000.00"), result.nssf());
        assertEquals(new BigDecimal("1375.00"), result.shif());
        assertEquals(new BigDecimal("750.00"), result.housing());
        assertEquals(new BigDecimal("44875.00"), result.taxable());
        assertEquals(new BigDecimal("5845.85"), result.paye());
        assertEquals(new BigDecimal("39029.15"), result.net());
    }

    @Test
    void usesYearThreeNssfCeilingForJanuary2026() {
        var result = calculator.calculate(YearMonth.of(2026, 1),
            new BigDecimal("100000"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, true);
        assertEquals(new BigDecimal("4320.00"), result.nssf());
    }

    @Test
    void appliesShifMinimumToAllSalariesWherePercentageIsBelowMinimum() {
        var low = calculator.calculate(YearMonth.of(2026, 2),
            new BigDecimal("5000"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, true);
        var belowMinimum = calculator.calculate(YearMonth.of(2026, 2),
            new BigDecimal("8000"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, true);
        var aboveMinimum = calculator.calculate(YearMonth.of(2026, 2),
            new BigDecimal("12000"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, true);

        assertEquals(new BigDecimal("300.00"), low.shif());
        assertEquals(new BigDecimal("300.00"), belowMinimum.shif());
        assertEquals(new BigDecimal("330.00"), aboveMinimum.shif());
    }

    @Test
    void excludesIrregularBonusFromRegularPayStatutoryContributionsButIncludesItInPayeGross() {
        var result = calculator.calculate(YearMonth.of(2026, 2),
            new BigDecimal("50000"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("10000"),
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, true);

        assertEquals(new BigDecimal("60000.00"), result.gross());
        assertEquals(new BigDecimal("3000.00"), result.nssf());
        assertEquals(new BigDecimal("1375.00"), result.shif());
        assertEquals(new BigDecimal("750.00"), result.housing());
        assertEquals(new BigDecimal("54875.00"), result.taxable());
        assertEquals(new BigDecimal("8845.85"), result.paye());
        assertEquals(new BigDecimal("46029.15"), result.net());
    }

    @Test
    void includesRegularMonthlyAllowancesInStatutoryContributionBases() {
        var result = calculator.calculate(YearMonth.of(2026, 2),
            new BigDecimal("50000"), new BigDecimal("5000"), new BigDecimal("2000"),
            new BigDecimal("10000"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, true);

        assertEquals(new BigDecimal("67000.00"), result.gross());
        assertEquals(new BigDecimal("3420.00"), result.nssf());
        assertEquals(new BigDecimal("1567.50"), result.shif());
        assertEquals(new BigDecimal("855.00"), result.housing());
        assertEquals(new BigDecimal("61157.50"), result.taxable());
        assertEquals(new BigDecimal("10730.60"), result.paye());
        assertEquals(new BigDecimal("50426.90"), result.net());
    }

    @Test
    void capsYearFourNssfAtSixPercentOfOneHundredEightThousand() {
        var result = calculator.calculate(YearMonth.of(2026, 2),
            new BigDecimal("150000"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, true);

        assertEquals(new BigDecimal("6480.00"), result.nssf());
        assertEquals(new BigDecimal("6480.00"), result.employerNssf());
    }

    @Test
    void doesNotApplyResidentPersonalReliefToNonResident() {
        var result = calculator.calculate(YearMonth.of(2026, 2),
            new BigDecimal("50000"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false);
        assertEquals(new BigDecimal("0.00"), result.personalRelief());
        assertEquals(new BigDecimal("8245.85"), result.paye());
    }
}
