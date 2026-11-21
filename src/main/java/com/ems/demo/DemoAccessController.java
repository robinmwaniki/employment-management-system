package com.ems.demo;

import com.ems.dto.response.AuthResponse;
import com.ems.security.jwt.JwtService;
import com.ems.security.service.CustomUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

/**
 * Issues short-lived demo tokens for synthetic, read-only demo accounts.
 * This controller is not registered unless the dedicated "demo" profile is active.
 */
@RestController
@Profile("demo")
@RequestMapping("/api/demo")
@RequiredArgsConstructor
public class DemoAccessController {

    private final CustomUserDetailsService userDetailsService;
    private final JwtService jwtService;

    @PostMapping("/login")
    public AuthResponse login(@RequestParam("role") String requestedRole) {
        String username = switch (requestedRole.toUpperCase(Locale.ROOT)) {
            case "ADMIN" -> "demo-admin";
            case "HR_MANAGER" -> "demo-hr";
            case "EMPLOYEE" -> "demo-employee";
            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Choose a supported demo role.");
        };

        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        return AuthResponse.builder()
                .accessToken(jwtService.generateToken(userDetails))
                .tokenType("Bearer")
                .build();
    }
}
