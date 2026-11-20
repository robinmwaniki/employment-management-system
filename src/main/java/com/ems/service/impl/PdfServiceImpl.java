package com.ems.service.impl;

import com.ems.entity.Payroll;
import com.ems.exception.ResourceNotFoundException;
import com.ems.repository.PayrollRepository;
import com.ems.service.interfaces.PdfService;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;

@Service
@RequiredArgsConstructor
public class PdfServiceImpl implements PdfService {

    private final PayrollRepository payrollRepository;

    @Override
    public byte[] generatePayslip(Long payrollId) {

        Payroll payroll = payrollRepository.findById(payrollId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Payroll not found with id: " + payrollId));

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        PdfWriter writer = new PdfWriter(outputStream);
        PdfDocument pdfDocument = new PdfDocument(writer);
        Document document = new Document(pdfDocument);

        document.add(new Paragraph("EMPLOYEE PAYSLIP"));
        document.add(new Paragraph(" "));

        document.add(new Paragraph("Employee: "
                + payroll.getEmployee().getFirstName()
                + " "
                + payroll.getEmployee().getLastName()));

        document.add(new Paragraph("Payroll Month: "
                + payroll.getPayrollMonth()));

        document.add(new Paragraph(""));

        document.add(new Paragraph("Basic Salary: "
                + payroll.getBasicSalary()));

        document.add(new Paragraph("House Allowance: "
                + payroll.getHouseAllowance()));

        document.add(new Paragraph("Transport Allowance: "
                + payroll.getTransportAllowance()));

        document.add(new Paragraph("Bonus: "
                + payroll.getBonus()));

        document.add(new Paragraph("Tax: "
                + payroll.getTax()));

        document.add(new Paragraph("Deductions: "
                + payroll.getDeductions()));

        document.add(new Paragraph("-----------------------------"));

        document.add(new Paragraph("Net Salary: "
                + payroll.getNetSalary()));

        document.close();

        return outputStream.toByteArray();
    }
}