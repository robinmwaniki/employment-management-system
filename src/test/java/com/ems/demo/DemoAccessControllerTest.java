package com.ems.demo;

import com.ems.dto.response.AuthResponse;
import com.ems.security.jwt.JwtService;
import com.ems.security.service.CustomUserDetailsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemoAccessControllerTest {

    @Mock
    private CustomUserDetailsService userDetailsService;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private DemoAccessController controller;

    @Test
    void issuesTokenForAdministratorDemoRole() {
        assertDemoToken("ADMIN", "demo-admin", "ROLE_ADMIN");
    }

    @Test
    void issuesTokenForHrDemoRole() {
        assertDemoToken("HR_MANAGER", "demo-hr", "ROLE_HR_MANAGER");
    }

    @Test
    void issuesTokenForEmployeeDemoRole() {
        assertDemoToken("EMPLOYEE", "demo-employee", "ROLE_EMPLOYEE");
    }

    @Test
    void rejectsUnsupportedRoleWithoutIssuingToken() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.login("SUPERUSER"));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    private void assertDemoToken(String requestedRole, String username, String authority) {
        UserDetails details = User.withUsername(username)
                .password("not-used-for-demo-token-exchange")
                .authorities(authority)
                .build();
        when(userDetailsService.loadUserByUsername(username)).thenReturn(details);
        when(jwtService.generateToken(details)).thenReturn("signed-demo-token");

        AuthResponse response = controller.login(requestedRole);

        assertEquals("signed-demo-token", response.getAccessToken());
        assertEquals("Bearer", response.getTokenType());
        verify(userDetailsService).loadUserByUsername(username);
        verify(jwtService).generateToken(details);
    }
}
