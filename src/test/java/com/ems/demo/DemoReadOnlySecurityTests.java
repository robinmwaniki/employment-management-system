package com.ems.demo;

import com.ems.TestcontainersConfiguration;
import com.ems.repository.AnnouncementRepository;
import com.ems.repository.AttendanceRepository;
import com.ems.repository.DepartmentRepository;
import com.ems.repository.EmployeeRepository;
import com.ems.repository.LeaveRepository;
import com.ems.repository.PayrollRepository;
import com.ems.repository.PerformanceRepository;
import org.springframework.boot.DefaultApplicationArguments;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the actual demo security chains against an isolated Testcontainers database.
 */
@SpringBootTest(properties = {
        "spring.profiles.active=demo",
        "DB_URL=jdbc:postgresql://localhost:5432/ems_demo_test",
        "DB_USERNAME=ems_demo_test",
        "DB_PASSWORD=ems_demo_test",
        "jwt.secret=bW9jay1kZW1vLXRlc3Qta2V5LWZvci1qd3Qtc2lnbmluZy0zMi1ieXRlcy1taW4="
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class DemoReadOnlySecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DemoDataInitializer demoDataInitializer;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private AnnouncementRepository announcementRepository;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private LeaveRepository leaveRepository;

    @Autowired
    private PayrollRepository payrollRepository;

    @Autowired
    private PerformanceRepository performanceRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void employeeCanReadOnlyTheirOwnSelfServiceData() throws Exception {
        String token = demoToken("EMPLOYEE");

        mockMvc.perform(get("/api/me/profile").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("amina.kamau@example.test"));

        mockMvc.perform(get("/api/me/payroll").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));

        mockMvc.perform(get("/api/employees").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void administratorCanReadStaffDataButCannotMutateIt() throws Exception {
        String token = demoToken("ADMIN");

        mockMvc.perform(get("/api/employees").header("Authorization", bearer(token)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/employees")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/employees/1")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void syntheticDemoSeedCanBeRepeatedWithoutCreatingDuplicates() throws Exception {
        DefaultApplicationArguments arguments = new DefaultApplicationArguments(new String[0]);

        demoDataInitializer.run(arguments);
        demoDataInitializer.run(arguments);

        org.assertj.core.api.Assertions.assertThat(departmentRepository.count()).isEqualTo(3);
        org.assertj.core.api.Assertions.assertThat(employeeRepository.count()).isEqualTo(4);
        org.assertj.core.api.Assertions.assertThat(announcementRepository.count()).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(attendanceRepository.count()).isEqualTo(5);
        org.assertj.core.api.Assertions.assertThat(leaveRepository.count()).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(payrollRepository.count()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(performanceRepository.count()).isEqualTo(1);
    }

    @Test
    void partialDemoSeedRepairsMissingRowsWithoutDuplicatingExistingData() {
        Employee employee = employeeRepository.findByEmail("amina.kamau@example.test")
                .orElseThrow();
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Africa/Nairobi"));

        announcementRepository.deleteAll();
        announcementRepository.save(com.ems.entity.Announcement.builder()
                .title("Unrelated announcement")
                .message("Existing non-demo data must not suppress demo fixtures.")
                .publishDate(today)
                .expiryDate(today.plusDays(30))
                .build());

        leaveRepository.deleteAll();
        leaveRepository.save(com.ems.entity.LeaveRequestEntity.builder()
                .employee(employee)
                .leaveType(com.ems.entity.LeaveType.ANNUAL)
                .startDate(today.plusDays(40))
                .endDate(today.plusDays(41))
                .reason("Unrelated existing leave")
                .status(com.ems.entity.LeaveStatus.CANCELLED)
                .appliedDate(today)
                .build());

        performanceRepository.deleteAll();
        performanceRepository.save(com.ems.entity.PerformanceReview.builder()
                .employee(employee)
                .reviewDate(today.minusDays(60))
                .reviewer("Unrelated reviewer")
                .rating(3)
                .comments("Existing non-demo review.")
                .goals("Existing non-demo goal.")
                .build());

        DefaultApplicationArguments arguments =
                new DefaultApplicationArguments(new String[0]);
        demoDataInitializer.run(arguments);
        demoDataInitializer.run(arguments);

        org.assertj.core.api.Assertions.assertThat(announcementRepository.count()).isEqualTo(3);
        org.assertj.core.api.Assertions.assertThat(leaveRepository.count()).isEqualTo(3);
        org.assertj.core.api.Assertions.assertThat(performanceRepository.count()).isEqualTo(2);
    }

    @Test
    void demoProfileDoesNotExposeLegacyThymeleafPages() throws Exception {
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isForbidden());
    }

    @Test
    void demoRoleExchangeRejectsUnknownRoles() throws Exception {
        mockMvc.perform(post("/api/demo/login").param("role", "SUPERUSER"))
                .andExpect(status().isBadRequest());
    }

    private String demoToken(String role) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/demo/login").param("role", role))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn();

        JsonNode response = objectMapper.readTree(
                result.getResponse().getContentAsString());
        return response.get("accessToken").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
