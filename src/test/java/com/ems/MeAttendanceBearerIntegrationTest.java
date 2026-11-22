package com.ems;

import com.ems.entity.Attendance;
import com.ems.entity.Department;
import com.ems.entity.Employee;
import com.ems.entity.Payroll;
import com.ems.entity.Role;
import com.ems.entity.User;
import com.ems.repository.AttendanceRepository;
import com.ems.repository.DepartmentRepository;
import com.ems.repository.EmployeeRepository;
import com.ems.repository.PayrollRepository;
import com.ems.repository.UserRepository;
import com.ems.security.jwt.JwtService;
import com.ems.security.service.CustomUserDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the actual bearer-token filter, self-service controller and
 * persistence services against the disposable PostgreSQL test database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MeAttendanceBearerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private PayrollRepository payrollRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Autowired
    private JwtService jwtService;

    @Test
    @Transactional
    void employeeBearerTokenCanReadOnlyItsSelfServiceAttendancePage()
            throws Exception {

        Department department = createDepartment("Integration Test Department", "ITD");
        Employee employee = createEmployee(
                department, "Test", "Employee",
                "attendance-employee@example.test", "+254700000101");

        createEmployeeUser(employee, "attendance-employee@example.test");

        String token = tokenFor("attendance-employee@example.test");

        mockMvc.perform(get("/api/me/attendance")
                        .param("page", "2")
                        .param("size", "7")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(2))
                .andExpect(jsonPath("$.size").value(7))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @Transactional
    void employeeBearerTokenCannotReadAnotherEmployeesAttendance()
            throws Exception {

        Department department = createDepartment(
                "Attendance Ownership Department", "AOD");

        Employee authenticatedEmployee = createEmployee(
                department, "Own", "Employee",
                "attendance-owner@example.test", "+254700000102");
        Employee otherEmployee = createEmployee(
                department, "Other", "Employee",
                "attendance-other@example.test", "+254700000103");

        createEmployeeUser(authenticatedEmployee, "attendance-owner@example.test");

        LocalDate attendanceDate = LocalDate.of(2026, 11, 20);
        attendanceRepository.saveAndFlush(Attendance.builder()
                .employee(authenticatedEmployee)
                .attendanceDate(attendanceDate)
                .checkInTime(LocalDateTime.of(2026, 11, 20, 8, 0))
                .late(false)
                .overtime(false)
                .build());
        attendanceRepository.saveAndFlush(Attendance.builder()
                .employee(otherEmployee)
                .attendanceDate(attendanceDate)
                .checkInTime(LocalDateTime.of(2026, 11, 20, 8, 5))
                .late(true)
                .overtime(false)
                .build());

        String token = tokenFor("attendance-owner@example.test");

        mockMvc.perform(get("/api/me/attendance")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].employeeId")
                        .value(authenticatedEmployee.getId()));
    }

    @Test
    @Transactional
    void employeeBearerTokenCannotDownloadAnotherEmployeesPayslip()
            throws Exception {

        Department department = createDepartment(
                "Payslip Ownership Department", "POD");

        Employee authenticatedEmployee = createEmployee(
                department, "Payslip", "Owner",
                "payslip-owner@example.test", "+254700000104");
        Employee otherEmployee = createEmployee(
                department, "Payslip", "Other",
                "payslip-other@example.test", "+254700000105");

        createEmployeeUser(authenticatedEmployee, "payslip-owner@example.test");
        Payroll otherEmployeesPayroll = createPayroll(otherEmployee);

        mockMvc.perform(get("/api/me/payroll/{payrollId}/payslip",
                        otherEmployeesPayroll.getId())
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + tokenFor("payslip-owner@example.test")))
                .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void employeeBearerTokenCanDownloadItsOwnPdfPayslip()
            throws Exception {

        Department department = createDepartment(
                "Payslip Download Department", "PDD");

        Employee employee = createEmployee(
                department, "PDF", "Employee",
                "payslip-pdf@example.test", "+254700000106");

        createEmployeeUser(employee, "payslip-pdf@example.test");
        Payroll payroll = createPayroll(employee);

        MvcResult result = mockMvc.perform(get(
                        "/api/me/payroll/{payrollId}/payslip", payroll.getId())
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + tokenFor("payslip-pdf@example.test")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString(
                                "Payslip-" + payroll.getId() + ".pdf")))
                .andReturn();

        assertTrue(result.getResponse().getContentType().startsWith(
                MediaType.APPLICATION_PDF_VALUE));
        byte[] pdf = result.getResponse().getContentAsByteArray();
        assertTrue(pdf.length > 4, "The payslip response should contain a PDF document");
        assertArrayEquals(new byte[]{'%', 'P', 'D', 'F'},
                Arrays.copyOf(pdf, 4));
    }

    private Department createDepartment(String name, String code) {
        return departmentRepository.saveAndFlush(
                Department.builder().name(name).code(code).build());
    }

    private Employee createEmployee(
            Department department,
            String firstName,
            String lastName,
            String email,
            String phoneNumber) {

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
        return payrollRepository.saveAndFlush(Payroll.builder()
                .employee(employee)
                .payrollMonth(YearMonth.of(2026, 10))
                .basicSalary(new BigDecimal("50000.00"))
                .houseAllowance(new BigDecimal("5000.00"))
                .transportAllowance(new BigDecimal("3000.00"))
                .bonus(new BigDecimal("0.00"))
                .deductions(new BigDecimal("500.00"))
                .tax(new BigDecimal("6500.00"))
                .netSalary(new BigDecimal("51000.00"))
                .grossPay(new BigDecimal("58000.00"))
                .taxablePay(new BigDecimal("58000.00"))
                .build());
    }

    private String tokenFor(String username) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        return jwtService.generateToken(userDetails);
    }
}
