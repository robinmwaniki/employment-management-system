package com.ems;

import com.ems.entity.Attendance;
import com.ems.entity.Department;
import com.ems.entity.Employee;
import com.ems.entity.Role;
import com.ems.entity.User;
import com.ems.repository.AttendanceRepository;
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
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the actual bearer-token filter, self-service controller and
 * attendance service against the disposable PostgreSQL test database.
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
    private UserRepository userRepository;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Autowired
    private JwtService jwtService;

    @Test
    @Transactional
    void employeeBearerTokenCanReadOnlyItsSelfServiceAttendancePage()
            throws Exception {

        Department department = departmentRepository.saveAndFlush(
                Department.builder()
                        .name("Integration Test Department")
                        .code("ITD")
                        .build());

        Employee employee = createEmployee(
                department,
                "Test",
                "Employee",
                "attendance-employee@example.test",
                "+254700000101");

        String username = "attendance-employee@example.test";
        userRepository.saveAndFlush(User.builder()
                .employee(employee)
                .username(username)
                .password("test-only-not-used-for-bearer-authentication")
                .role(Role.ROLE_EMPLOYEE)
                .enabled(true)
                .build());

        String token = tokenFor(username);

        mockMvc.perform(get("/api/me/attendance")
                        .param("page", "2")
                        .param("size", "7")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(2))
                .andExpect(jsonPath("$.size").value(7))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @Transactional
    void employeeBearerTokenCannotReadAnotherEmployeesAttendance()
            throws Exception {

        Department department = departmentRepository.saveAndFlush(
                Department.builder()
                        .name("Attendance Ownership Department")
                        .code("AOD")
                        .build());

        Employee authenticatedEmployee = createEmployee(
                department,
                "Own",
                "Employee",
                "attendance-owner@example.test",
                "+254700000102");
        Employee otherEmployee = createEmployee(
                department,
                "Other",
                "Employee",
                "attendance-other@example.test",
                "+254700000103");

        userRepository.saveAndFlush(User.builder()
                .employee(authenticatedEmployee)
                .username("attendance-owner@example.test")
                .password("test-only-not-used-for-bearer-authentication")
                .role(Role.ROLE_EMPLOYEE)
                .enabled(true)
                .build());

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
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].employeeId")
                        .value(authenticatedEmployee.getId()));
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

    private String tokenFor(String username) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        return jwtService.generateToken(userDetails);
    }
}
