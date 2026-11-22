package com.ems;

import com.ems.entity.Department;
import com.ems.entity.Employee;
import com.ems.entity.LeaveRequestEntity;
import com.ems.entity.LeaveStatus;
import com.ems.entity.LeaveType;
import com.ems.entity.Role;
import com.ems.entity.User;
import com.ems.repository.DepartmentRepository;
import com.ems.repository.EmployeeRepository;
import com.ems.repository.LeaveRepository;
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises employee leave listing and application through the real JWT
 * security filter, self-service controller and disposable PostgreSQL database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MeLeaveBearerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private LeaveRepository leaveRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Autowired
    private JwtService jwtService;

    @Test
    @Transactional
    void employeeCanOnlyListItsOwnLeaveRequests() throws Exception {

        Department department = createDepartment();
        Employee owner = createEmployee(
                department,
                "Leave",
                "Owner",
                "leave-owner@example.test",
                "+254700000301");
        Employee other = createEmployee(
                department,
                "Leave",
                "Other",
                "leave-other@example.test",
                "+254700000302");

        createEmployeeUser(owner, "leave-owner@example.test");
        createEmployeeUser(other, "leave-other@example.test");

        createLeave(owner, LocalDate.of(2026, 12, 1));
        createLeave(other, LocalDate.of(2026, 12, 8));

        mockMvc.perform(get("/api/me/leave")
                        .param("page", "0")
                        .param("size", "5")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + tokenFor("leave-owner@example.test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].employeeId").value(owner.getId()));
    }

    @Test
    @Transactional
    void leaveApplicationUsesAuthenticatedEmployeeInsteadOfSubmittedEmployeeId()
            throws Exception {

        Department department = createDepartment();
        Employee authenticatedEmployee = createEmployee(
                department,
                "Leave",
                "Applicant",
                "leave-applicant@example.test",
                "+254700000303");
        Employee otherEmployee = createEmployee(
                department,
                "Leave",
                "Impersonated",
                "leave-impersonated@example.test",
                "+254700000304");

        createEmployeeUser(authenticatedEmployee, "leave-applicant@example.test");

        String requestBody = """
                {
                  "employeeId": %d,
                  "leaveType": "ANNUAL",
                  "startDate": "2026-12-15",
                  "endDate": "2026-12-17",
                  "reason": "Integration test leave request"
                }
                """.formatted(otherEmployee.getId());

        mockMvc.perform(post("/api/me/leave")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + tokenFor("leave-applicant@example.test"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.employeeId")
                        .value(authenticatedEmployee.getId()))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    private Department createDepartment() {

        return departmentRepository.saveAndFlush(Department.builder()
                .name("Leave Integration Department")
                .code("LID")
                .build());
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

    private void createLeave(Employee employee, LocalDate startDate) {

        leaveRepository.saveAndFlush(LeaveRequestEntity.builder()
                .employee(employee)
                .leaveType(LeaveType.ANNUAL)
                .startDate(startDate)
                .endDate(startDate.plusDays(2))
                .reason("Synthetic integration fixture")
                .status(LeaveStatus.PENDING)
                .appliedDate(LocalDate.of(2026, 11, 22))
                .build());
    }

    private String tokenFor(String username) {

        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        return jwtService.generateToken(userDetails);
    }
}
