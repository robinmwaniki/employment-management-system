package com.ems;

import com.ems.entity.Announcement;
import com.ems.entity.Department;
import com.ems.entity.Employee;
import com.ems.entity.Role;
import com.ems.entity.User;
import com.ems.repository.AnnouncementRepository;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the employee announcements endpoint returns only currently
 * published announcements to an authenticated employee.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MeAnnouncementsBearerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AnnouncementRepository announcementRepository;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Autowired
    private JwtService jwtService;

    @Test
    @Transactional
    void employeeOnlyReceivesAnnouncementsWithinTheirPublicationWindow() throws Exception {

        Department department = departmentRepository.saveAndFlush(
                Department.builder()
                        .name("Announcement Integration Department")
                        .code("ANID")
                        .build());

        Employee employee = employeeRepository.saveAndFlush(Employee.builder()
                .firstName("Announcement")
                .lastName("Reader")
                .email("announcement-reader@example.test")
                .phoneNumber("+254700000901")
                .salary(new BigDecimal("50000.00"))
                .hireDate(LocalDate.of(2025, 1, 1))
                .department(department)
                .build());

        userRepository.saveAndFlush(User.builder()
                .employee(employee)
                .username("announcement-reader@example.test")
                .password("test-only-not-used-for-bearer-authentication")
                .role(Role.ROLE_EMPLOYEE)
                .enabled(true)
                .build());

        LocalDate today = LocalDate.now(ZoneId.of("Africa/Nairobi"));

        createAnnouncement("Currently active", today, today);
        createAnnouncement("No date restrictions", null, null);
        createAnnouncement("Not published yet", today.plusDays(1), null);
        createAnnouncement("Already expired", null, today.minusDays(1));

        mockMvc.perform(get("/api/me/announcements")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + tokenFor("announcement-reader@example.test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.title == 'Currently active')]").exists())
                .andExpect(jsonPath("$[?(@.title == 'No date restrictions')]").exists())
                .andExpect(jsonPath("$[?(@.title == 'Not published yet')]").doesNotExist())
                .andExpect(jsonPath("$[?(@.title == 'Already expired')]").doesNotExist());
    }

    private void createAnnouncement(
            String title,
            LocalDate publishDate,
            LocalDate expiryDate) {

        announcementRepository.saveAndFlush(Announcement.builder()
                .title(title)
                .message("Synthetic integration-test announcement")
                .publishDate(publishDate)
                .expiryDate(expiryDate)
                .build());
    }

    private String tokenFor(String username) {

        return jwtService.generateToken(
                userDetailsService.loadUserByUsername(username));
    }
}
