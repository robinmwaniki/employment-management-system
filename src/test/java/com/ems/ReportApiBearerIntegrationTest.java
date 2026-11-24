package com.ems;

import com.ems.dto.response.CompanyProfileResponse;
import com.ems.dto.response.StatutoryRemittanceReportResponse;
import com.ems.entity.Role;
import com.ems.entity.User;
import com.ems.repository.UserRepository;
import com.ems.security.jwt.JwtService;
import com.ems.security.service.CustomUserDetailsService;
import com.ems.service.impl.StatutoryRemittanceReportService;
import com.ems.service.interfaces.ReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies React report endpoints through real JWT bearer authentication,
 * including staff role restrictions and the statutory CSV download contract.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ReportApiBearerIntegrationTest {

    private static final YearMonth MONTH = YearMonth.of(2026, 10);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private ReportService reportService;

    @MockitoBean
    private StatutoryRemittanceReportService statutoryReportService;

    @Test
    @Transactional
    void administratorAndHrManagerCanReadReportsAndDownloadStatutoryCsv()
            throws Exception {
        stubReportServices();
        createUser("reports-admin@example.test", Role.ROLE_ADMIN);
        createUser("reports-hr@example.test", Role.ROLE_HR_MANAGER);

        for (String username : List.of(
                "reports-admin@example.test", "reports-hr@example.test")) {
            String token = tokenFor(username);

            mockMvc.perform(get("/api/reports/employees").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isOk()).andExpect(content().json("[]"));
            mockMvc.perform(get("/api/reports/payroll").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isOk()).andExpect(content().json("[]"));
            mockMvc.perform(get("/api/reports/leave").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isOk()).andExpect(content().json("[]"));
            mockMvc.perform(get("/api/reports/attendance").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isOk()).andExpect(content().json("[]"));
            mockMvc.perform(get("/api/reports/performance").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isOk()).andExpect(content().json("[]"));
            mockMvc.perform(get("/api/reports/statutory-remittances")
                            .param("month", MONTH.toString())
                            .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.payrollMonth").value(MONTH.toString()));

            mockMvc.perform(get("/api/reports/statutory-remittances.csv")
                            .param("month", MONTH.toString())
                            .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("text/csv")))
                    .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=statutory-remittances-" + MONTH + ".csv"))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString(
                            "Company,\"Acme, Kenya Ltd\"")))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString(
                            "42,\"Amina, Wanjiku\",\"A123456789Z\"")));
        }
    }

    @Test
    @Transactional
    void employeeCannotReadAnyStaffReportOrDownloadStatutoryCsv()
            throws Exception {
        createUser("reports-employee@example.test", Role.ROLE_EMPLOYEE);
        String token = tokenFor("reports-employee@example.test");

        for (String path : List.of(
                "/api/reports/employees",
                "/api/reports/payroll",
                "/api/reports/leave",
                "/api/reports/attendance",
                "/api/reports/performance",
                "/api/reports/statutory-remittances",
                "/api/reports/statutory-remittances.csv")) {
            var request = get(path).header(HttpHeaders.AUTHORIZATION, bearer(token));
            if (path.contains("statutory-remittances")) {
                request.param("month", MONTH.toString());
            }
            mockMvc.perform(request).andExpect(status().isForbidden());
        }
    }

    private void stubReportServices() {
        when(reportService.employeeReport()).thenReturn(List.of());
        when(reportService.payrollReport()).thenReturn(List.of());
        when(reportService.leaveReport()).thenReturn(List.of());
        when(reportService.attendanceReport()).thenReturn(List.of());
        when(reportService.performanceReport()).thenReturn(List.of());
        when(statutoryReportService.generate(MONTH)).thenReturn(statutoryReport());
    }

    private StatutoryRemittanceReportResponse statutoryReport() {
        BigDecimal amount = new BigDecimal("1250.00");
        CompanyProfileResponse company = new CompanyProfileResponse(
                "Acme, Kenya Ltd", "P051234567A", "NSSF-EMP-1", "SHA-EMP-1",
                "Nairobi", "+254700000000", "payroll@example.test");
        var employee = new StatutoryRemittanceReportResponse.EmployeeLine(
                42L, "Amina, Wanjiku", "A123456789Z", "NSSF-42", "SHA-42",
                amount, amount, amount, amount, amount, amount);
        var obligation = new StatutoryRemittanceReportResponse.Obligation(
                "PAYE", "Pay As You Earn", amount, BigDecimal.ZERO,
                amount, LocalDate.of(2026, 11, 9),
                "On or before the 9th day of the following month", "KRA");
        return new StatutoryRemittanceReportResponse(
                MONTH, company, 1, 0, 0, 0,
                amount, amount, amount, amount, amount, amount, amount,
                amount, List.of(obligation), List.of(employee));
    }

    private void createUser(String username, Role role) {
        userRepository.saveAndFlush(User.builder()
                .username(username)
                .password("test-only-not-used-for-bearer-authentication")
                .role(role)
                .enabled(true)
                .build());
    }

    private String tokenFor(String username) {
        return jwtService.generateToken(
                userDetailsService.loadUserByUsername(username));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
