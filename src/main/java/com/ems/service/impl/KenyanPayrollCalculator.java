package com.ems.service.impl;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;

/** Calculates Kenyan monthly payroll deductions using the statutory rates effective for the pay period. */
@Component
public class KenyanPayrollCalculator {
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final BigDecimal SIX_PERCENT = new BigDecimal("0.06");
    private static final BigDecimal SHIF_RATE = new BigDecimal("0.0275");
    private static final BigDecimal HOUSING_RATE = new BigDecimal("0.015");
    private static final BigDecimal PERSONAL_RELIEF = new BigDecimal("2400.00");
    private static final BigDecimal PENSION_LIMIT = new BigDecimal("30000.00");
    private static final BigDecimal SHIF_MINIMUM = new BigDecimal("300.00");

    /** Immutable breakdown of employee deductions, employer contributions and take-home pay. */
    public record Result(
            BigDecimal gross,
            BigDecimal nssf,
            BigDecimal shif,
            BigDecimal housing,
            BigDecimal employerNssf,
            BigDecimal employerHousing,
            BigDecimal pension,
            BigDecimal taxable,
            BigDecimal personalRelief,
            BigDecimal insuranceRelief,
            BigDecimal paye,
            BigDecimal otherDeductions,
            BigDecimal net) {
    }

    /**
     * Calculates monthly payroll using cash earnings and the statutory rules applicable to the month.
     *
     * @param month payroll month used to select the NSSF earnings ceiling
     * @param basic employee basic salary
     * @param house regular monthly housing allowance
     * @param transport regular monthly transport allowance
     * @param bonus irregular bonus, included in PAYE gross earnings but excluded from regular-pay levies
     * @param otherDeductions additional employee deductions
     * @param pensionContribution employee pension contribution
     * @param insurancePremium qualifying monthly insurance premiums
     * @param resident whether the employee qualifies for resident personal relief
     * @return calculated payroll breakdown
     */
    public Result calculate(
            YearMonth month,
            BigDecimal basic,
            BigDecimal house,
            BigDecimal transport,
            BigDecimal bonus,
            BigDecimal otherDeductions,
            BigDecimal pensionContribution,
            BigDecimal insurancePremium,
            boolean resident) {

        // PAYE applies to cash employment income, including bonuses.
        BigDecimal gross = add(basic, house, transport, bonus);

        // SHIF and Housing Levy use basic salary plus regular monthly cash allowances.
        // NSSF pensionable wages exclude fluctuating emoluments such as irregular bonuses.
        BigDecimal regularMonthlyPay = add(basic, house, transport);

        BigDecimal upperNssfLimit = month.isBefore(YearMonth.of(2026, 2))
                ? new BigDecimal("72000.00")
                : new BigDecimal("108000.00");

        BigDecimal nssf = regularMonthlyPay.min(upperNssfLimit)
                .multiply(SIX_PERCENT)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal shif = regularMonthlyPay.multiply(SHIF_RATE)
                .max(SHIF_MINIMUM)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal housing = regularMonthlyPay.multiply(HOUSING_RATE)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal pension = zero(pensionContribution);
        BigDecimal taxable = gross
                .subtract(nssf.add(pension).min(PENSION_LIMIT))
                .subtract(shif)
                .subtract(housing)
                .max(ZERO)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal personalRelief = resident
                ? PERSONAL_RELIEF
                : ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal insuranceRelief = zero(insurancePremium)
                .multiply(new BigDecimal("0.15"))
                .min(new BigDecimal("5000.00"))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal paye = progressiveTax(taxable)
                .subtract(personalRelief)
                .subtract(insuranceRelief)
                .max(ZERO)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal other = zero(otherDeductions);
        BigDecimal net = gross
                .subtract(nssf)
                .subtract(shif)
                .subtract(housing)
                .subtract(pension)
                .subtract(paye)
                .subtract(other)
                .setScale(2, RoundingMode.HALF_UP);

        return new Result(
                gross, nssf, shif, housing, nssf, housing, pension,
                taxable, personalRelief, insuranceRelief, paye, other, net);
    }

    private static BigDecimal progressiveTax(BigDecimal income) {
        BigDecimal remaining = income;
        BigDecimal tax = ZERO;
        BigDecimal[] bands = {
                new BigDecimal("24000.00"),
                new BigDecimal("8333.00"),
                new BigDecimal("467667.00"),
                new BigDecimal("300000.00")
        };
        BigDecimal[] rates = {
                new BigDecimal("0.10"),
                new BigDecimal("0.25"),
                new BigDecimal("0.30"),
                new BigDecimal("0.325"),
                new BigDecimal("0.35")
        };

        for (int i = 0; i < bands.length; i++) {
            BigDecimal slice = remaining.min(bands[i]);
            if (slice.signum() > 0) {
                tax = tax.add(slice.multiply(rates[i]));
            }
            remaining = remaining.subtract(slice);
        }
        if (remaining.signum() > 0) {
            tax = tax.add(remaining.multiply(rates[4]));
        }
        return tax.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal add(BigDecimal... values) {
        BigDecimal total = ZERO;
        for (BigDecimal value : values) {
            total = total.add(zero(value));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal zero(BigDecimal value) {
        return value == null ? ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }
}
