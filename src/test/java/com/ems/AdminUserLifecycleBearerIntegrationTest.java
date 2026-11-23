package com.ems;

import com.ems.entity.Department;
import com.ems.entity.Employee;
import com.ems.entity.Role;
import com.ems.entity.User;
import com.ems.repository.DepartmentRepository;
import com.ems.repository.EmployeeRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises administrator system-user lifecycle operations through the real
 * JWT bearer filter, REST controller, service, password encoder and database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AdminUserLifecycleBearerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @Transactional
    void administratorCanCreateUpdateDisableEnableAndDeleteUserAccounts()
            throws Exception {

        Department department = departmentRepository.saveAndFlush(
                Department.builder()
                        .name("User Lifecycle Integration Department")
                        .code("ULID")
                        .build());

        Employee employee = employeeRepository.saveAndFlush(Employee.builder()
                .firstName("Lifecycle")
                .lastName("Employee")
                .email("user-lifecycle-employee@example.test")
                .phoneNumber("+254700000951")
                .salary(new BigDecimal("50000.00"))
                .hireDate(LocalDate.of(2025, 1, 1))
                .department(department)
                .build());

        User administrator = userRepository.saveAndFlush(User.builder()
                .username("user-lifecycle-admin@example.test")
                .password("test-only-not-used-for-bearer-authentication")
                .role(Role.ROLE_ADMIN)
                .enabled(true)
                .build());

        String token = jwtService.generateToken(
                userDetailsService.loadUserByUsername(administrator.getUsername()));

        MvcResult created = mockMvc.perform(post("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "employeeId": %d,
                                  "username": "managed-user@example.test",
                                  "password": "InitialPassword123!",
                                  "role": "ROLE_EMPLOYEE"
                                }
                                """.formatted(employee.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("managed-user@example.test"))
                .andExpect(jsonPath("$.role").value("ROLE_EMPLOYEE"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andReturn();

        Long managedUserId = com.fasterxml.jackson.databind.json.JsonMapper.builder()
                .build()
                .readTree(created.getResponse().getContentAsString())
                .get("id")
                .asLong();

        User managedUser = userRepository.findById(managedUserId).orElseThrow();
        assertNotEquals("InitialPassword123!", managedUser.getPassword());
        assertTrue(passwordEncoder.matches("InitialPassword123!", managedUser.getPassword()));

        mockMvc.perform(put("/api/users/{id}", managedUserId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "employeeId": %d,
                                  "username": "managed-user-updated@example.test",
                                  "password": "",
                                  "role": "ROLE_HR_MANAGER"
                                }
                                """.formatted(employee.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("managed-user-updated@example.test"))
                .andExpect(jsonPath("$.role").value("ROLE_HR_MANAGER"));

        assertTrue(passwordEncoder.matches(
                "InitialPassword123!",
                userRepository.findById(managedUserId).orElseThrow().getPassword()));

        mockMvc.perform(post("/api/users/{id}/disable", managedUserId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
        assertFalse(userRepository.findById(managedUserId).orElseThrow().getEnabled());

        mockMvc.perform(post("/api/users/{id}/enable", managedUserId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));
        assertTrue(userRepository.findById(managedUserId).orElseThrow().getEnabled());

        mockMvc.perform(delete("/api/users/{id}", managedUserId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());
        assertFalse(userRepository.existsById(managedUserId));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
