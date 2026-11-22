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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the employee payroll feed is scoped to the employee linked to
 * the authenticated bearer token.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MePayrollBearerIntegrationTest {

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

    @Test
    @Transactional
    void employeeCanOnlyReadItsOwnPayrollRecords() throws Exception {

        Department department = departmentRepository.saveAndFlush(
                Department.builder()
                        .name("Payroll Integration Department")
                        .code("PYID")
                        .build());

        Employee owner = createEmployee(
                department,
                "Payroll",
                "Owner",
                "payroll-owner@example.test",
                "+254700000801");
        Employee other = createEmployee(
                department,
                "Payroll",
                "Other",
                "payroll-other@example.test",
                "+254700000802");

        createEmployeeUser(owner, "payroll-owner@example.test");
        createEmployeeUser(other, "payroll-other@example.test");

        Payroll ownPayroll = createPayroll(owner, YearMonth.of(2026, 9), "72000.00");
        createPayroll(other, YearMonth.of(2026, 9), "81000.00");

        mockMvc.perform(get("/api/me/payroll")
                        .param("page", "0")
                        .param("size", "5")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + tokenFor("payroll-owner@example.test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(ownPayroll.getId()))
                .andExpect(jsonPath("$.content[0].employeeId").value(owner.getId()))
                .andExpect(jsonPath("$.content[0].netSalary").value(72000.00));
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

    private Payroll createPayroll(Employee employee, YearMonth month, String netSalary) {

        BigDecimal zero = BigDecimal.ZERO.setScale(2);
        return payrollRepository.saveAndFlush(Payroll.builder()
                .employee(employee)
                .payrollMonth(month)
                .basicSalary(new BigDecimal("50000.00"))
                .houseAllowance(new BigDecimal("10000.00"))
                .transportAllowance(new BigDecimal("5000.00"))
                .bonus(zero)
                .deductions(zero)
                .tax(zero)
                .netSalary(new BigDecimal(netSalary))
                .build());
    }

    private String tokenFor(String username) {

        return jwtService.generateToken(
                userDetailsService.loadUserByUsername(username));
    }
}
