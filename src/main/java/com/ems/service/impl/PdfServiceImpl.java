package com.ems.service.impl;

import com.ems.entity.Payroll;
import com.ems.exception.ResourceNotFoundException;
import com.ems.repository.PayrollRepository;
import com.ems.service.interfaces.PdfService;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

/** Generates a detailed Kenyan payroll payslip PDF. */
@Service
@RequiredArgsConstructor
public class PdfServiceImpl implements PdfService {
    private final PayrollRepository payrollRepository;
    @Override
    public byte[] generatePayslip(Long payrollId) {
        Payroll p=payrollRepository.findById(payrollId)
            .orElseThrow(()->new ResourceNotFoundException("Payroll not found with id: "+payrollId));
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        Document doc=new Document(new PdfDocument(new PdfWriter(out)));
        NumberFormat kes=NumberFormat.getNumberInstance(Locale.forLanguageTag("en-KE"));
        kes.setMinimumFractionDigits(2);kes.setMaximumFractionDigits(2);
        doc.add(new Paragraph("EMPLOYEE PAYSLIP").setBold().setFontSize(18));
        doc.add(new Paragraph("Currency: KES | Payroll period: "+p.getPayrollMonth()));
        doc.add(new Paragraph("Employee: "+p.getEmployee().getFirstName()+" "+p.getEmployee().getLastName()));
        doc.add(new Paragraph("EARNINGS").setBold());
        Table earnings=new Table(2).useAllAvailableWidth();
        row(earnings,"Basic salary",p.getBasicSalary(),kes);row(earnings,"House allowance",p.getHouseAllowance(),kes);
        row(earnings,"Transport allowance",p.getTransportAllowance(),kes);row(earnings,"Bonus",p.getBonus(),kes);
        row(earnings,"Gross pay",p.getGrossPay(),kes);doc.add(earnings);
        doc.add(new Paragraph("STATUTORY DEDUCTIONS").setBold());
        Table deductions=new Table(2).useAllAvailableWidth();
        row(deductions,"NSSF employee contribution",p.getNssf(),kes);
        row(deductions,"SHIF (2.75% of gross; KES 300 minimum)",p.getShif(),kes);
        row(deductions,"Affordable Housing Levy (1.5%)",p.getHousingLevy(),kes);
        row(deductions,"Voluntary pension contribution",p.getPensionContribution(),kes);
        row(deductions,"Taxable pay",p.getTaxablePay(),kes);
        row(deductions,"PAYE after applicable relief",p.getTax(),kes);
        row(deductions,"Other deductions",p.getDeductions(),kes);doc.add(deductions);
        doc.add(new Paragraph("RELIEFS").setBold());
        doc.add(new Paragraph("Personal relief: KES "+kes.format(nvl(p.getPersonalRelief()))));
        doc.add(new Paragraph("Insurance relief: KES "+kes.format(nvl(p.getInsuranceRelief()))));
        doc.add(new Paragraph("NET PAY: KES "+kes.format(p.getNetSalary())).setBold().setFontSize(15));
        doc.add(new Paragraph("EMPLOYER CONTRIBUTIONS (not deducted from employee net pay)").setBold());
        doc.add(new Paragraph("Employer NSSF: KES "+kes.format(p.getEmployerNssf())));
        doc.add(new Paragraph("Employer Housing Levy: KES "+kes.format(p.getEmployerHousingLevy())));
        doc.add(new Paragraph("PAYE is calculated progressively on taxable employment income. This payslip records the rates and calculations used for this payroll period."));
        doc.close();return out.toByteArray();
    }
    private static void row(Table t,String label,BigDecimal value,NumberFormat f){t.addCell(label);t.addCell("KES "+f.format(nvl(value)));}
    private static BigDecimal nvl(BigDecimal v){return v==null?BigDecimal.ZERO:v;}
}