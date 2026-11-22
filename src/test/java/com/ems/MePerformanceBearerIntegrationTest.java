package com.ems;

import com.ems.entity.Department;
import com.ems.entity.Employee;
import com.ems.entity.PerformanceReview;
import com.ems.entity.Role;
import com.ems.entity.User;
import com.ems.repository.DepartmentRepository;
import com.ems.repository.EmployeeRepository;
import com.ems.repository.PerformanceRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies performance review history is scoped to the employee linked to
 * the authenticated bearer token.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MePerformanceBearerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private PerformanceRepository performanceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Autowired
    private JwtService jwtService;

    @Test
    @Transactional
    void employeeCanOnlyReadItsOwnPerformanceReviews() throws Exception {

        Department department = departmentRepository.saveAndFlush(
                Department.builder()
                        .name("Performance Integration Department")
                        .code("PFID")
                        .build());

        Employee owner = createEmployee(
                department,
                "Performance",
                "Owner",
                "performance-owner@example.test",
                "+254700000701");
        Employee other = createEmployee(
                department,
                "Performance",
                "Other",
                "performance-other@example.test",
                "+254700000702");

        createEmployeeUser(owner, "performance-owner@example.test");
        createEmployeeUser(other, "performance-other@example.test");

        createReview(owner, "Owner review");
        createReview(other, "Other employee review");

        mockMvc.perform(get("/api/me/performance")
                        .param("page", "0")
                        .param("size", "5")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + tokenFor("performance-owner@example.test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].employeeId").value(owner.getId()))
                .andExpect(jsonPath("$.content[0].comments").value("Owner review"));
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

    private void createReview(Employee employee, String comments) {

        performanceRepository.saveAndFlush(PerformanceReview.builder()
                .employee(employee)
                .reviewDate(LocalDate.of(2026, 11, 1))
                .reviewer("Synthetic HR reviewer")
                .rating(4)
                .comments(comments)
                .goals("Synthetic test goal")
                .build());
    }

    private String tokenFor(String username) {

        return jwtService.generateToken(
                userDetailsService.loadUserByUsername(username));
    }
}
