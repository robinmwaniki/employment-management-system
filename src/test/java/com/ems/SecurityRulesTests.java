package com.ems;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Guards the access rules: who may open which page or endpoint.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SecurityRulesTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void anonymousUserIsSentToLoginFromStaffPages() throws Exception {

        mockMvc.perform(get("/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void anonymousApiCallIsUnauthorized() throws Exception {

        mockMvc.perform(get("/api/employees"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void registrationIsClosedToAnonymousUsers() throws Exception {

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"intruder\",\"password\":\"Password12345\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongCredentialsAreRejectedWithoutDetails() throws Exception {

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nobody\",\"password\":\"wrong-password-1\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message")
                        .value("Invalid credentials or account unavailable."));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void employeeCannotOpenStaffPagesOrTheStaffApi() throws Exception {

        mockMvc.perform(get("/employees")).andExpect(status().isForbidden());
        mockMvc.perform(get("/payroll")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/employees")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "HR_MANAGER")
    void hrManagerCannotOpenAdministratorPages() throws Exception {

        mockMvc.perform(get("/users")).andExpect(status().isForbidden());
        mockMvc.perform(get("/system-logs")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteActionsRejectGetRequests() throws Exception {

        mockMvc.perform(get("/employees/delete/1"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void logoutRequiresACsrfToken() throws Exception {

        mockMvc.perform(post("/logout"))
                .andExpect(status().isForbidden());
    }
}
