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
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the real bearer-token filter against persisted account state.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class JwtBearerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Autowired
    private JwtService jwtService;

    /**
     * Confirms that a valid employee JWT authenticates through the configured
     * filter while the same identity remains blocked from staff-only APIs.
     */
    @Test
    @Transactional
    void validEmployeeBearerTokenAuthenticatesAndCannotAccessStaffApi()
            throws Exception {

        String username = "jwt-employee@example.test";
        userRepository.saveAndFlush(User.builder()
                .username(username)
                .password("test-only-not-used-for-bearer-authentication")
                .role(Role.ROLE_EMPLOYEE)
                .enabled(true)
                .build());

        String token = tokenFor(username);

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.role").value("ROLE_EMPLOYEE"));

        mockMvc.perform(get("/api/employees")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    /**
     * Confirms that an invalid bearer token never creates an authenticated
     * security context.
     */
    @Test
    void invalidBearerTokenIsRejected() throws Exception {

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer not-a-valid-jwt"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Confirms that disabling an account revokes access even when its JWT has
     * not expired, because the filter reloads the account for every request.
     */
    @Test
    @Transactional
    void disabledAccountCannotUsePreviouslyIssuedBearerToken()
            throws Exception {

        String username = "jwt-disabled@example.test";
        User user = userRepository.saveAndFlush(User.builder()
                .username(username)
                .password("test-only-not-used-for-bearer-authentication")
                .role(Role.ROLE_ADMIN)
                .enabled(true)
                .build());

        String token = tokenFor(username);

        user.setEnabled(false);
        userRepository.saveAndFlush(user);

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    private String tokenFor(String username) {

        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        return jwtService.generateToken(userDetails);
    }
}
