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
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises password changes through the real JWT filter and REST controller.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthChangePasswordBearerIntegrationTest {

    private static final String CURRENT_PASSWORD = "CurrentPass123";
    private static final String NEW_PASSWORD = "NewPassword456";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Autowired
    private JwtService jwtService;

    @Test
    @Transactional
    void passwordChangeRequiresMatchingConfirmationAndUpdatesThePassword()
            throws Exception {

        User user = createUser("password-change@example.test");
        String token = tokenFor(user.getUsername());

        mockMvc.perform(post("/api/auth/change-password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword": "CurrentPass123",
                                  "newPassword": "NewPassword456",
                                  "confirmPassword": "DifferentPassword789"
                                }
                                """))
                .andExpect(status().isBadRequest());

        assertTrue(passwordEncoder.matches(
                CURRENT_PASSWORD,
                userRepository.findById(user.getId()).orElseThrow().getPassword()));

        mockMvc.perform(post("/api/auth/change-password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword": "CurrentPass123",
                                  "newPassword": "NewPassword456",
                                  "confirmPassword": "NewPassword456"
                                }
                                """))
                .andExpect(status().isNoContent());

        User persisted = userRepository.findById(user.getId()).orElseThrow();
        assertTrue(passwordEncoder.matches(NEW_PASSWORD, persisted.getPassword()));
    }

    @Test
    @Transactional
    void incorrectCurrentPasswordIsRejectedWithoutChangingThePassword()
            throws Exception {

        User user = createUser("password-current@example.test");
        String token = tokenFor(user.getUsername());

        mockMvc.perform(post("/api/auth/change-password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword": "WrongCurrent123",
                                  "newPassword": "NewPassword456",
                                  "confirmPassword": "NewPassword456"
                                }
                                """))
                .andExpect(status().isUnauthorized());

        User persisted = userRepository.findById(user.getId()).orElseThrow();
        assertTrue(passwordEncoder.matches(CURRENT_PASSWORD, persisted.getPassword()));
    }

    private User createUser(String username) {

        return userRepository.saveAndFlush(User.builder()
                .username(username)
                .password(passwordEncoder.encode(CURRENT_PASSWORD))
                .role(Role.ROLE_EMPLOYEE)
                .enabled(true)
                .build());
    }

    private String tokenFor(String username) {

        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        return jwtService.generateToken(userDetails);
    }
}
