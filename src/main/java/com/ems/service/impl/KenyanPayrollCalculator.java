package com.ems.service.impl;

import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;

/** Calculates Kenyan monthly statutory payroll deductions using the rates effective for the pay period. */
@Component
public class KenyanPayrollCalculator {
    private static final BigDecimal ZERO=money("0");
    private static final BigDecimal SIX=money("0.06");
    private static final BigDecimal SHIF_RATE=money("0.0275");
    private static final BigDecimal HOUSING_RATE=money("0.015");
    private static final BigDecimal PERSONAL_RELIEF=money("2400");
    private static final BigDecimal PENSION_LIMIT=money("30000");
    private static final BigDecimal SHIF_MIN=money("300");

    public record Result(BigDecimal gross, BigDecimal nssf, BigDecimal shif, BigDecimal housing,
        BigDecimal employerNssf, BigDecimal employerHousing, BigDecimal pension, BigDecimal taxable,
        BigDecimal personalRelief, BigDecimal insuranceRelief, BigDecimal paye, BigDecimal otherDeductions,
        BigDecimal net) {}

    public Result calculate(YearMonth month, BigDecimal basic, BigDecimal house, BigDecimal transport,
        BigDecimal bonus, BigDecimal otherDeductions, BigDecimal pensionContribution,
        BigDecimal insurancePremium, boolean resident) {
        BigDecimal gross=add(basic,house,transport,bonus);
        BigDecimal lower=month.isBefore(YearMonth.of(2026,2))?money("8000"):money("9000");
        BigDecimal upper=month.isBefore(YearMonth.of(2026,2))?money("72000"):money("108000");
        BigDecimal pensionable=gross.min(upper);
        BigDecimal nssf=pensionable.multiply(SIX).setScale(2,RoundingMode.HALF_UP);
        BigDecimal shif=gross.multiply(SHIF_RATE).max(SHIF_MIN).setScale(2,RoundingMode.HALF_UP);
        BigDecimal housing=gross.multiply(HOUSING_RATE).setScale(2,RoundingMode.HALF_UP);
        BigDecimal pension=zero(pensionContribution);
        BigDecimal allowablePension=nssf.add(pension).min(PENSION_LIMIT);
        BigDecimal taxable=gross.subtract(allowablePension).subtract(shif).subtract(housing).max(ZERO);
        BigDecimal grossTax=progressiveTax(taxable);
        BigDecimal personal=resident?PERSONAL_RELIEF:ZERO;
        BigDecimal insurance=zero(insurancePremium).multiply(money("0.15"))
            .min(money("5000")).setScale(2,RoundingMode.HALF_UP);
        BigDecimal paye=grossTax.subtract(personal).subtract(insurance).max(ZERO).setScale(2,RoundingMode.HALF_UP);
        BigDecimal other=zero(otherDeductions);
        BigDecimal net=gross.subtract(nssf).subtract(shif).subtract(housing).subtract(pension).subtract(paye).subtract(other);
        return new Result(gross,nssf,shif,housing,nssf,housing,pension,taxable,personal,insurance,paye,other,net.setScale(2,RoundingMode.HALF_UP));
    }
    private static BigDecimal progressiveTax(BigDecimal income) {
        BigDecimal remaining=income, tax=ZERO;
        BigDecimal[] bands={money("24000"),money("8333"),money("467667"),money("300000")};
        BigDecimal[] rates={money("0.10"),money("0.25"),money("0.30"),money("0.325"),money("0.35")};
        for(int i=0;i<bands.length;i++){BigDecimal slice=remaining.min(bands[i]); if(slice.signum()>0) tax=tax.add(slice.multiply(rates[i])); remaining=remaining.subtract(slice);}
        if(remaining.signum()>0) tax=tax.add(remaining.multiply(rates[4]));
        return tax.setScale(2,RoundingMode.HALF_UP);
    }
    private static BigDecimal add(BigDecimal... values){BigDecimal total=ZERO;for(BigDecimal v:values)total=total.add(zero(v));return total.setScale(2,RoundingMode.HALF_UP);}
    private static BigDecimal zero(BigDecimal v){return v==null?ZERO:v;}
    private static BigDecimal money(String v){return new BigDecimal(v);}
}