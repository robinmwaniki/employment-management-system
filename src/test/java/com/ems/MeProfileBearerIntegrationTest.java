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
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies employee profile reads and edits operate only on the employee
 * linked to the authenticated bearer-token principal.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MeProfileBearerIntegrationTest {

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

    @Test
    @Transactional
    void employeeCanReadAndUpdateOnlyItsOwnContactProfile() throws Exception {

        Department department = departmentRepository.saveAndFlush(
                Department.builder()
                        .name("Profile Integration Department")
                        .code("PRID")
                        .build());

        Employee employee = createEmployee(
                department,
                "Profile",
                "Before",
                "profile-before@example.test",
                "+254700000601");
        Employee other = createEmployee(
                department,
                "Profile",
                "Other",
                "profile-other@example.test",
                "+254700000602");

        createEmployeeUser(employee, "profile-owner@example.test");
        createEmployeeUser(other, "profile-other@example.test");

        String token = tokenFor("profile-owner@example.test");

        mockMvc.perform(get("/api/me/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(employee.getId()))
                .andExpect(jsonPath("$.firstName").value("Profile"))
                .andExpect(jsonPath("$.email").value("profile-before@example.test"));

        mockMvc.perform(put("/api/me/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Updated",
                                  "lastName": "Employee",
                                  "email": "profile-updated@example.test",
                                  "phoneNumber": "+254700000699"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(employee.getId()))
                .andExpect(jsonPath("$.firstName").value("Updated"))
                .andExpect(jsonPath("$.lastName").value("Employee"))
                .andExpect(jsonPath("$.email").value("profile-updated@example.test"))
                .andExpect(jsonPath("$.phoneNumber").value("+254700000699"));

        Employee persisted = employeeRepository.findById(employee.getId()).orElseThrow();
        Employee untouched = employeeRepository.findById(other.getId()).orElseThrow();

        assertEquals(new BigDecimal("50000.00"), persisted.getSalary());
        assertEquals(department.getId(), persisted.getDepartment().getId());
        assertEquals(LocalDate.of(2025, 1, 1), persisted.getHireDate());
        assertEquals("profile-other@example.test", untouched.getEmail());
        assertEquals("Profile", untouched.getFirstName());
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

        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        return jwtService.generateToken(userDetails);
    }
}
