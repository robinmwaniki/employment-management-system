package com.ems;

import com.ems.entity.Role;
import com.ems.entity.User;
import com.ems.repository.UserRepository;
import com.ems.security.jwt.JwtService;
import com.ems.security.service.CustomUserDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies staff-vs-admin API authorization with real signed bearer tokens.
 * This deliberately exercises the JWT filter and method security together.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class StaffRoleBearerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Autowired
    private JwtService jwtService;

    @Test
    @Transactional
    void hrManagerCanUseStaffApisButCannotAdministerSystemUsers()
            throws Exception {
        String username = "role-matrix-hr@example.test";
        createUser(username, Role.ROLE_HR_MANAGER);
        String token = tokenFor(username);

        mockMvc.perform(get("/api/employees")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    @Transactional
    void administratorCanUseStaffAndSystemUserApis()
            throws Exception {
        String username = "role-matrix-admin@example.test";
        createUser(username, Role.ROLE_ADMIN);
        String token = tokenFor(username);

        mockMvc.perform(get("/api/employees")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @Transactional
    void employeeCannotUseStaffOrSystemUserApis()
            throws Exception {
        String username = "role-matrix-employee@example.test";
        createUser(username, Role.ROLE_EMPLOYEE);
        String token = tokenFor(username);

        mockMvc.perform(get("/api/employees")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden());
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
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        return jwtService.generateToken(userDetails);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
