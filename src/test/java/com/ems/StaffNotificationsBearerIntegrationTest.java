package com.ems;

import com.ems.entity.Notification;
import com.ems.entity.Role;
import com.ems.entity.User;
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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the React staff notification REST contract through real signed
 * bearer tokens, role checks, persistence and notification state changes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class StaffNotificationsBearerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

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
    void hrManagerCanListAndAcknowledgeNotificationsButEmployeeCannot()
            throws Exception {

        Notification first = createNotification(
                "Staff notification one", "First notification");
        Notification second = createNotification(
                "Staff notification two", "Second notification");

        createUser("staff-notifications-hr@example.test", Role.ROLE_HR_MANAGER);
        createUser("staff-notifications-employee@example.test", Role.ROLE_EMPLOYEE);

        String hrToken = tokenFor("staff-notifications-hr@example.test");
        String employeeToken = tokenFor("staff-notifications-employee@example.test");

        mockMvc.perform(get("/api/notifications")
                        .header(HttpHeaders.AUTHORIZATION, bearer(hrToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.title == 'Staff notification one')]").exists())
                .andExpect(jsonPath("$[?(@.title == 'Staff notification two')]").exists());

        mockMvc.perform(post("/api/notifications/{id}/read", first.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(hrToken)))
                .andExpect(status().isNoContent());

        assertTrue(notificationRepository.findById(first.getId())
                .orElseThrow()
                .isRead());

        mockMvc.perform(post("/api/notifications/read-all")
                        .header(HttpHeaders.AUTHORIZATION, bearer(hrToken)))
                .andExpect(status().isNoContent());

        assertTrue(notificationRepository.findById(second.getId())
                .orElseThrow()
                .isRead());

        mockMvc.perform(get("/api/notifications")
                        .header(HttpHeaders.AUTHORIZATION, bearer(employeeToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/notifications/{id}/read", first.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(employeeToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/notifications/read-all")
                        .header(HttpHeaders.AUTHORIZATION, bearer(employeeToken)))
                .andExpect(status().isForbidden());
    }

    private Notification createNotification(String title, String message) {
        return notificationRepository.saveAndFlush(Notification.builder()
                .title(title)
                .message(message)
                .isRead(false)
                .build());
    }

    private void createUser(String username, Role role) {
        userRepository.saveAndFlush(User.builder()
                .username(username)
                .password("test-only-not-used-for-bearer-authentication")
                .role(role)
                .enabled(true)
                .build());
    }

    private String tokenFor(String username) {
        return jwtService.generateToken(
                userDetailsService.loadUserByUsername(username));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
