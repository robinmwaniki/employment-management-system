package com.ems;

import com.ems.entity.Department;
import com.ems.entity.Employee;
import com.ems.entity.Notification;
import com.ems.entity.Role;
import com.ems.entity.User;
import com.ems.repository.DepartmentRepository;
import com.ems.repository.EmployeeRepository;
import com.ems.repository.NotificationRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies employee notification feeds, unread counts and read operations
 * are scoped to the employee identified by the bearer token.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MeNotificationsBearerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Autowired
    private JwtService jwtService;

    @Test
    @Transactional
    void employeeCanOnlyReadAndAcknowledgeItsOwnNotifications() throws Exception {

        Department department = departmentRepository.saveAndFlush(
                Department.builder()
                        .name("Notification Integration Department")
                        .code("NID")
                        .build());

        Employee owner = createEmployee(
                department,
                "Notification",
                "Owner",
                "notification-owner@example.test",
                "+254700000501");
        Employee other = createEmployee(
                department,
                "Notification",
                "Other",
                "notification-other@example.test",
                "+254700000502");

        createEmployeeUser(owner, "notification-owner@example.test");
        createEmployeeUser(other, "notification-other@example.test");

        Notification ownerNotification = createNotification(
                owner, "Owner notice", "Visible only to the owner");
        createNotification(other, "Other notice", "Must not appear in the owner feed");

        String ownerToken = tokenFor("notification-owner@example.test");

        mockMvc.perform(get("/api/me/notifications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Owner notice"));

        mockMvc.perform(get("/api/me/notifications/unread-count")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().string("1"));

        Long otherNotificationId = notificationRepository.findAll().stream()
                .filter(notification -> notification.getEmployee().getId().equals(other.getId()))
                .findFirst()
                .orElseThrow()
                .getId();

        mockMvc.perform(post("/api/me/notifications/{id}/read", otherNotificationId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/me/notifications/{id}/read", ownerNotification.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/me/notifications/unread-count")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().string("0"));
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

    private Notification createNotification(Employee employee, String title, String message) {

        return notificationRepository.saveAndFlush(Notification.builder()
                .employee(employee)
                .title(title)
                .message(message)
                .isRead(false)
                .build());
    }

    private String tokenFor(String username) {

        return jwtService.generateToken(
                userDetailsService.loadUserByUsername(username));
    }
}
