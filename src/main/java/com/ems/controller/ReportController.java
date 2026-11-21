package com.ems.controller;

import com.ems.dto.response.*;
import com.ems.service.impl.StatutoryRemittanceReportService;
import com.ems.service.interfaces.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {
    private final ReportService reportService;
    private final StatutoryRemittanceReportService statutoryReportService;

    @PreAuthorize("hasAnyRole('ADMIN','HR_MANAGER')")
    @GetMapping("/employees")
    public List<EmployeeResponse> employees() { return reportService.employeeReport(); }

    @PreAuthorize("hasAnyRole('ADMIN','HR_MANAGER')")
    @GetMapping("/payroll")
    public List<PayrollResponse> payroll() { return reportService.payrollReport(); }

    @PreAuthorize("hasAnyRole('ADMIN','HR_MANAGER')")
    @GetMapping("/leave")
    public List<LeaveResponse> leave() { return reportService.leaveReport(); }

    @PreAuthorize("hasAnyRole('ADMIN','HR_MANAGER')")
    @GetMapping("/attendance")
    public List<AttendanceResponse> attendance() { return reportService.attendanceReport(); }

    @PreAuthorize("hasAnyRole('ADMIN','HR_MANAGER')")
    @GetMapping("/performance")
    public List<PerformanceResponse> performance() { return reportService.performanceReport(); }

    @PreAuthorize("hasAnyRole('ADMIN','HR_MANAGER')")
    @GetMapping("/statutory-remittances")
    public StatutoryRemittanceReportResponse statutoryRemittances(@RequestParam YearMonth month) {
        return statutoryReportService.generate(month);
    }

    @PreAuthorize("hasAnyRole('ADMIN','HR_MANAGER')")
    @GetMapping(value = "/statutory-remittances.csv", produces = "text/csv")
    public ResponseEntity<byte[]> statutoryRemittancesCsv(@RequestParam YearMonth month) {
        var report = statutoryReportService.generate(month);
        StringBuilder csv = new StringBuilder();
        csv.append("Company,").append(cell(report.company().companyName())).append("\r\n")
           .append("Company KRA PIN,").append(cell(report.company().kraPin())).append("\r\n")
           .append("Company NSSF Employer Number,").append(cell(report.company().nssfEmployerNumber())).append("\r\n")
           .append("Company SHA Employer Number,").append(cell(report.company().shaEmployerNumber())).append("\r\n")
           .append("Payroll Month,").append(report.payrollMonth()).append("\r\n")
           .append("Employees,").append(report.employeeCount()).append("\r\n")
           .append("Gross Pay Total,").append(report.grossPayTotal()).append("\r\n")
           .append("PAYE Total,").append(report.payeTotal()).append("\r\n")
           .append("Employee NSSF Total,").append(report.employeeNssfTotal()).append("\r\n")
           .append("Employer NSSF Total,").append(report.employerNssfTotal()).append("\r\n")
           .append("SHIF Total,").append(report.shifTotal()).append("\r\n")
           .append("Employee Housing Levy Total,").append(report.employeeHousingLevyTotal()).append("\r\n")
           .append("Employer Housing Levy Total,").append(report.employerHousingLevyTotal()).append("\r\n")
           .append("Total Remittance,").append(report.totalRemittance()).append("\r\n\r\n")
           .append("Employee ID,Employee Name,KRA PIN,NSSF Number,SHA Number,PAYE,Employee NSSF,Employer NSSF,SHIF,Employee Housing Levy,Employer Housing Levy\r\n");
        for (var line : report.employees()) {
            csv.append(line.employeeId()).append(',').append(cell(line.employeeName())).append(',')
               .append(cell(line.kraPin())).append(',').append(cell(line.nssfNumber())).append(',')
               .append(cell(line.shaNumber())).append(',').append(line.paye()).append(',')
               .append(line.employeeNssf()).append(',').append(line.employerNssf()).append(',')
               .append(line.shif()).append(',').append(line.employeeHousingLevy()).append(',')
               .append(line.employerHousingLevy()).append("\r\n");
        }
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=statutory-remittances-" + month + ".csv")
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String cell(String value) {
        return "\"" + (value == null ? "" : value).replace("\"", "\"\"") + "\"";
    }
}
