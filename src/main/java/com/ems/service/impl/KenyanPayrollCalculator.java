package com.ems.service.impl;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
/** Calculates Kenyan monthly statutory payroll deductions using the rates effective for the pay period. */
@Component
public class KenyanPayrollCalculator {
 private static final BigDecimal ZERO=new BigDecimal("0"), SIX=new BigDecimal("0.06"), SHIF_RATE=new BigDecimal("0.0275"), HOUSING_RATE=new BigDecimal("0.015"), PERSONAL_RELIEF=new BigDecimal("2400"), PENSION_LIMIT=new BigDecimal("30000"), SHIF_MIN=new BigDecimal("300");
 public record Result(BigDecimal gross,BigDecimal nssf,BigDecimal shif,BigDecimal housing,BigDecimal employerNssf,BigDecimal employerHousing,BigDecimal pension,BigDecimal taxable,BigDecimal personalRelief,BigDecimal insuranceRelief,BigDecimal paye,BigDecimal otherDeductions,BigDecimal net){}
 public Result calculate(YearMonth month,BigDecimal basic,BigDecimal house,BigDecimal transport,BigDecimal bonus,BigDecimal otherDeductions,BigDecimal pensionContribution,BigDecimal insurancePremium,boolean resident){
  BigDecimal gross=add(basic,house,transport,bonus);
  BigDecimal upper=month.isBefore(YearMonth.of(2026,2))?new BigDecimal("72000"):new BigDecimal("108000");
  BigDecimal nssf=gross.min(upper).multiply(SIX).setScale(2,RoundingMode.HALF_UP);
  BigDecimal shif=gross.multiply(SHIF_RATE).max(SHIF_MIN).setScale(2,RoundingMode.HALF_UP);
  BigDecimal housing=gross.multiply(HOUSING_RATE).setScale(2,RoundingMode.HALF_UP);
  BigDecimal pension=zero(pensionContribution);
  BigDecimal taxable=gross.subtract(nssf.add(pension).min(PENSION_LIMIT)).subtract(shif).subtract(housing).max(ZERO);
  BigDecimal personal=resident?PERSONAL_RELIEF:ZERO;
  BigDecimal insurance=zero(insurancePremium).multiply(new BigDecimal("0.15")).min(new BigDecimal("5000")).setScale(2,RoundingMode.HALF_UP);
  BigDecimal paye=progressiveTax(taxable).subtract(personal).subtract(insurance).max(ZERO).setScale(2,RoundingMode.HALF_UP);
  BigDecimal other=zero(otherDeductions);
  BigDecimal net=gross.subtract(nssf).subtract(shif).subtract(housing).subtract(pension).subtract(paye).subtract(other).setScale(2,RoundingMode.HALF_UP);
  return new Result(gross,nssf,shif,housing,nssf,housing,pension,taxable,personal,insurance,paye,other,net);
 }
 private static BigDecimal progressiveTax(BigDecimal income){
  BigDecimal remaining=income,tax=ZERO;
  BigDecimal[] bands={new BigDecimal("24000"),new BigDecimal("8333"),new BigDecimal("467667"),new BigDecimal("300000")};
  BigDecimal[] rates={new BigDecimal("0.10"),new BigDecimal("0.25"),new BigDecimal("0.30"),new BigDecimal("0.325"),new BigDecimal("0.35")};
  for(int i=0;i<bands.length;i++){BigDecimal slice=remaining.min(bands[i]);if(slice.signum()>0)tax=tax.add(slice.multiply(rates[i]));remaining=remaining.subtract(slice);}
  if(remaining.signum()>0)tax=tax.add(remaining.multiply(rates[4]));
  return tax.setScale(2,RoundingMode.HALF_UP);
 }
 private static BigDecimal add(BigDecimal... values){BigDecimal total=ZERO;for(BigDecimal value:values)total=total.add(zero(value));return total.setScale(2,RoundingMode.HALF_UP);}
 private static BigDecimal zero(BigDecimal value){return value==null?ZERO:value;}
}