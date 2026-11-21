package com.ems.service.impl;

import com.ems.entity.Payroll;
import com.ems.exception.ResourceNotFoundException;
import com.ems.repository.PayrollRepository;
import com.ems.service.interfaces.PdfService;
import com.itextpdf.kernel.colors.Color;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class PdfServiceImpl implements PdfService {
    private static final Color INK=new DeviceRgb(35,39,72);
    private static final Color PURPLE=new DeviceRgb(100,86,220);
    private static final Color PURPLE_LIGHT=new DeviceRgb(241,239,255);
    private static final Color TEAL=new DeviceRgb(18,153,125);
    private static final Color TEAL_LIGHT=new DeviceRgb(231,249,244);
    private static final Color MUTED=new DeviceRgb(111,116,139);
    private static final Color LINE=new DeviceRgb(229,231,240);
    private static final Color WHITE=new DeviceRgb(255,255,255);
    private final PayrollRepository payrollRepository;
    @Value("${ems.company.name:EMS Workforce}")
    private String companyName;

    @Override
    public byte[] generatePayslip(Long payrollId) {
        Payroll p=payrollRepository.findById(payrollId)
            .orElseThrow(()->new ResourceNotFoundException("Payroll not found with id: "+payrollId));
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        Document doc=new Document(new PdfDocument(new PdfWriter(out)));
        doc.setMargins(34,38,38,38);
        String employee=p.getEmployee().getFirstName()+" "+p.getEmployee().getLastName();
        String month=p.getPayrollMonth()==null?"—":p.getPayrollMonth().toString();

        Table banner=new Table(UnitValue.createPercentArray(new float[]{68,32})).useAllAvailableWidth();
        banner.addCell(new Cell().setBackgroundColor(PURPLE).setBorder(Border.NO_BORDER).setPadding(18)
            .add(new Paragraph(safe(companyName)).setFontColor(WHITE).setFontSize(17))
            .add(new Paragraph("EMPLOYEE PAYSLIP").setFontColor(new DeviceRgb(225,222,255)).setFontSize(9)));
        banner.addCell(new Cell().setBackgroundColor(INK).setBorder(Border.NO_BORDER).setPadding(18).setTextAlignment(TextAlignment.RIGHT)
            .add(new Paragraph("PAY PERIOD").setFontColor(new DeviceRgb(190,192,215)).setFontSize(8))
            .add(new Paragraph(month).setFontColor(WHITE).setFontSize(15)));
        doc.add(banner).add(new Paragraph(" "));

        Table details=new Table(UnitValue.createPercentArray(new float[]{50,50})).useAllAvailableWidth();
        details.addCell(info("EMPLOYEE",employee));
        details.addCell(info("PAYROLL REFERENCE","PS-"+p.getId()));
        details.addCell(info("DEPARTMENT",p.getEmployee().getDepartment()==null?"—":safe(p.getEmployee().getDepartment().getName())));
        details.addCell(info("EMAIL",safe(p.getEmployee().getEmail())));
        doc.add(details).add(new Paragraph(" "));

        Table earnings=section("EARNINGS",PURPLE);
        amount(earnings,"Basic salary",p.getBasicSalary());
        amount(earnings,"House allowance",p.getHouseAllowance());
        amount(earnings,"Transport allowance",p.getTransportAllowance());
        amount(earnings,"Bonus",p.getBonus());
        BigDecimal gross=p.getGrossPay()==null?sum(p.getBasicSalary(),p.getHouseAllowance(),p.getTransportAllowance(),p.getBonus()):p.getGrossPay();
        total(earnings,"GROSS PAY",gross,PURPLE_LIGHT,INK);
        doc.add(earnings).add(new Paragraph(" "));

        Table deductions=section("EMPLOYEE DEDUCTIONS",TEAL);
        amount(deductions,"PAYE (after applicable reliefs)",p.getTax());
        amount(deductions,"NSSF",p.getNssf());
        amount(deductions,"SHIF",p.getShif());
        amount(deductions,"Affordable Housing Levy",p.getHousingLevy());
        amount(deductions,"Employee pension contribution",p.getPensionContribution());
        amount(deductions,"Other deductions",p.getDeductions());
        BigDecimal deducted=sum(p.getTax(),p.getNssf(),p.getShif(),p.getHousingLevy(),p.getPensionContribution(),p.getDeductions());
        total(deductions,"TOTAL EMPLOYEE DEDUCTIONS",deducted,TEAL_LIGHT,INK);
        doc.add(deductions).add(new Paragraph(" "));

        Table net=new Table(UnitValue.createPercentArray(new float[]{58,42})).useAllAvailableWidth();
        net.addCell(new Cell().setBorder(Border.NO_BORDER).setBackgroundColor(INK).setPadding(15)
            .add(new Paragraph("NET PAY").setFontColor(WHITE).setFontSize(9))
            .add(new Paragraph("Amount payable to employee").setFontColor(new DeviceRgb(202,204,226)).setFontSize(8)));
        net.addCell(new Cell().setBorder(Border.NO_BORDER).setBackgroundColor(TEAL).setPadding(15).setTextAlignment(TextAlignment.RIGHT)
            .add(new Paragraph("KES "+money(p.getNetSalary())).setFontColor(WHITE).setFontSize(19)));
        doc.add(net).add(new Paragraph(" "));

        Table notes=new Table(UnitValue.createPercentArray(new float[]{50,50})).useAllAvailableWidth();
        notes.addCell(note("TAXABLE PAY",p.getTaxablePay()));
        notes.addCell(note("PERSONAL RELIEF",p.getPersonalRelief()));
        notes.addCell(note("INSURANCE RELIEF",p.getInsuranceRelief()));
        notes.addCell(note("INSURANCE PREMIUM",p.getInsurancePremium()));
        notes.addCell(note("EMPLOYER NSSF (not deducted)",p.getEmployerNssf()));
        notes.addCell(note("EMPLOYER HOUSING LEVY (not deducted)",p.getEmployerHousingLevy()));
        doc.add(notes).add(new Paragraph(" "));
        doc.add(new Paragraph("This payslip is a confidential payroll record. Statutory amounts are calculated using the rates applicable to the payroll period.")
            .setFontColor(MUTED).setFontSize(8).setTextAlignment(TextAlignment.CENTER));
        doc.add(new Paragraph(safe(companyName)+"  •  Generated electronically")
            .setFontColor(MUTED).setFontSize(8).setTextAlignment(TextAlignment.CENTER));
        doc.close();
        return out.toByteArray();
    }

    private static Table section(String title,Color accent) {
        Table t=new Table(UnitValue.createPercentArray(new float[]{68,32})).useAllAvailableWidth();
        t.addHeaderCell(new Cell(1,2).setBackgroundColor(accent).setBorder(Border.NO_BORDER).setPadding(9)
            .add(new Paragraph(title).setFontColor(WHITE).setFontSize(9)));
        return t;
    }
    private static void amount(Table t,String label,BigDecimal value) {
        t.addCell(new Cell().setBorderBottom(new SolidBorder(LINE,.5f)).setPadding(8)
            .add(new Paragraph(label).setFontColor(INK).setFontSize(9)));
        t.addCell(new Cell().setBorderBottom(new SolidBorder(LINE,.5f)).setPadding(8).setTextAlignment(TextAlignment.RIGHT)
            .add(new Paragraph("KES "+money(value)).setFontColor(INK).setFontSize(9)));
    }
    private static void total(Table t,String label,BigDecimal value,Color bg,Color fg) {
        t.addCell(new Cell().setBackgroundColor(bg).setBorder(Border.NO_BORDER).setPadding(9)
            .add(new Paragraph(label).setFontColor(fg).setFontSize(8)));
        t.addCell(new Cell().setBackgroundColor(bg).setBorder(Border.NO_BORDER).setPadding(9).setTextAlignment(TextAlignment.RIGHT)
            .add(new Paragraph("KES "+money(value)).setFontColor(fg).setFontSize(9)));
    }
    private static Cell info(String label,String value) {
        return new Cell().setBorder(new SolidBorder(LINE,.6f)).setPadding(10)
            .add(new Paragraph(label).setFontColor(MUTED).setFontSize(7))
            .add(new Paragraph(value).setFontColor(INK).setFontSize(9));
    }
    private static Cell note(String label,BigDecimal value) {
        return new Cell().setBorder(new SolidBorder(LINE,.5f)).setPadding(8)
            .add(new Paragraph(label).setFontColor(MUTED).setFontSize(7))
            .add(new Paragraph("KES "+money(value)).setFontColor(INK).setFontSize(9));
    }
    private static BigDecimal sum(BigDecimal... values) {
        BigDecimal total=BigDecimal.ZERO;
        for(BigDecimal value:values)if(value!=null)total=total.add(value);
        return total;
    }
    private static String money(BigDecimal value) {
        NumberFormat f=NumberFormat.getNumberInstance(Locale.US);
        f.setMinimumFractionDigits(2);f.setMaximumFractionDigits(2);
        return f.format(value==null?BigDecimal.ZERO:value);
    }
    private static String safe(String value) { return value==null||value.isBlank()?"—":value; }
}
