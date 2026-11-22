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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies self-service leave cancellation is restricted to the employee
 * associated with the bearer token.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MeLeaveCancellationBearerIntegrationTest {

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
    void onlyTheOwnerCanCancelItsOwnPendingLeaveRequest() throws Exception {

        Department department = departmentRepository.saveAndFlush(
                Department.builder()
                        .name("Leave Cancellation Integration Department")
                        .code("LCID")
                        .build());

        Employee owner = createEmployee(
                department,
                "Cancel",
                "Owner",
                "leave-cancel-owner@example.test",
                "+254700000401");
        Employee other = createEmployee(
                department,
                "Cancel",
                "Other",
                "leave-cancel-other@example.test",
                "+254700000402");

        createEmployeeUser(owner, "leave-cancel-owner@example.test");
        createEmployeeUser(other, "leave-cancel-other@example.test");

        LeaveRequestEntity leave = leaveRepository.saveAndFlush(
                LeaveRequestEntity.builder()
                        .employee(owner)
                        .leaveType(LeaveType.ANNUAL)
                        .startDate(LocalDate.of(2026, 12, 21))
                        .endDate(LocalDate.of(2026, 12, 23))
                        .reason("Synthetic cancellation ownership fixture")
                        .status(LeaveStatus.PENDING)
                        .appliedDate(LocalDate.of(2026, 11, 22))
                        .build());

        mockMvc.perform(post("/api/me/leave/{id}/cancel", leave.getId())
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + tokenFor("leave-cancel-other@example.test")))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/me/leave/{id}/cancel", leave.getId())
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + tokenFor("leave-cancel-owner@example.test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(leave.getId()))
                .andExpect(jsonPath("$.employeeId").value(owner.getId()))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
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

    private String tokenFor(String username) {

        return jwtService.generateToken(
                userDetailsService.loadUserByUsername(username));
    }
}
