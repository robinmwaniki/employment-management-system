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
    void appliesKraShifLowIncomeMinimumOnlyBelowSixThousand() {
        var low = calculator.calculate(YearMonth.of(2026, 2),
            new BigDecimal("5000"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, true);
        var aboveThreshold = calculator.calculate(YearMonth.of(2026, 2),
            new BigDecimal("8000"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, true);
        assertEquals(new BigDecimal("300.00"), low.shif());
        assertEquals(new BigDecimal("220.00"), aboveThreshold.shif());
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