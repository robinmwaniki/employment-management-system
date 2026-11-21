package com.ems.demo;

import com.ems.TestcontainersConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the actual demo security chains against an isolated Testcontainers database.
 */
@SpringBootTest(properties = "spring.profiles.active=demo")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class DemoReadOnlySecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
