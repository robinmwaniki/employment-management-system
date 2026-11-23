package com.ems;

import com.ems.entity.Department;
import com.ems.entity.Employee;
import com.ems.entity.Payroll;
import com.ems.entity.Role;
import com.ems.entity.User;
import com.ems.repository.DepartmentRepository;
import com.ems.repository.EmployeeRepository;
import com.ems.repository.PayrollRepository;
import com.ems.repository.UserRepository;
import com.ems.security.jwt.JwtService;
import com.ems.security.service.CustomUserDetailsService;
import com.ems.service.interfaces.PdfService;
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

import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises employee payslip downloads through the real JWT filter and a
 * disposable PostgreSQL database, while isolating PDF rendering itself.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MePayslipBearerIntegrationTest {

    private static final byte[] PDF_BYTES = new byte[] {
            '%', 'P', 'D', 'F', '-', '1', '.', '7', '\n', '%', 'T', 'E', 'S', 'T'
    };

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private PayrollRepository payrollRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private PdfService pdfService;

    @Test
    @Transactional
    void employeeCanDownloadOwnPayslipWithPdfHttpContract() throws Exception {
        Department department = departmentRepository.saveAndFlush(
                Department.builder()
                        .name("Payslip Download Integration Department")
                        .code("PDID")
                        .build());
        Employee owner = createEmployee(department, "Payslip", "Owner",
                "payslip-owner@example.test", "+254700000951");
        Employee other = createEmployee(department, "Payslip", "Other",
                "payslip-other@example.test", "+254700000952");
        createEmployeeUser(owner, "payslip-owner@example.test");
        createEmployeeUser(other, "payslip-other@example.test");
        Payroll ownPayroll = createPayroll(owner);
        Payroll otherPayroll = createPayroll(other);
        when(pdfService.generatePayslip(ownPayroll.getId())).thenReturn(PDF_BYTES);

        mockMvc.perform(get("/api/me/payroll/{payrollId}/payslip", ownPayroll.getId())
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + tokenFor("payslip-owner@example.test")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"Payslip-" + ownPayroll.getId() + ".pdf\""))
                .andExpect(content().bytes(PDF_BYTES));

        verify(pdfService).generatePayslip(ownPayroll.getId());
        clearInvocations(pdfService);

        mockMvc.perform(get("/api/me/payroll/{payrollId}/payslip", otherPayroll.getId())
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + tokenFor("payslip-owner@example.test")))
                .andExpect(status().isNotFound());

        verifyNoInteractions(pdfService);
    }

    private Employee createEmployee(Department department, String firstName,
            String lastName, String email, String phoneNumber) {
        return employeeRepository.saveAndFlush(Employee.builder()
                .firstName(firstName)
                .lastName(lastName)
                .email(email)
                .phoneNumber(phoneNumber)
                .salary(new BigDecimal("50000.00"))
                .hireDate(LocalDate.of(2025, 1, 1))
                .department(department)
                .build());
    }

    private void createEmployeeUser(Employee employee, String username) {
        userRepository.saveAndFlush(User.builder()
                .employee(employee)
                .username(username)
                .password("test-only-not-used-for-bearer-authentication")
                .role(Role.ROLE_EMPLOYEE)
                .enabled(true)
                .build());
    }

    private Payroll createPayroll(Employee employee) {
        BigDecimal zero = BigDecimal.ZERO.setScale(2);
        return payrollRepository.saveAndFlush(Payroll.builder()
                .employee(employee)
                .payrollMonth(YearMonth.of(2026, 10))
                .basicSalary(new BigDecimal("50000.00"))
                .houseAllowance(new BigDecimal("10000.00"))
                .transportAllowance(new BigDecimal("5000.00"))
                .bonus(zero)
                .deductions(zero)
                .tax(zero)
                .netSalary(new BigDecimal("65000.00"))
                .build());
    }

    private String tokenFor(String username) {
        return jwtService.generateToken(
                userDetailsService.loadUserByUsername(username));
    }
}
