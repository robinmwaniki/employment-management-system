package com.ems.controller;

import com.ems.dto.request.ChangePasswordRequest;
import com.ems.dto.request.LoginRequest;
import com.ems.dto.request.RegisterRequest;
import com.ems.dto.response.AuthResponse;
import com.ems.dto.response.MeResponse;
import com.ems.entity.User;
import com.ems.repository.UserRepository;
import com.ems.security.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication endpoints used by both the web UI and the React frontend.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final UserRepository userRepository;

    @PostMapping("/login")
    public AuthResponse login(
            @Valid @RequestBody LoginRequest request) {

        return userService.login(request);
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public AuthResponse register(
            @Valid @RequestBody RegisterRequest request) {

        return userService.register(request);
    }

    /**
     * Returns the signed-in user's identity so the React app can personalise
     * the UI without an extra profile call.
     */
    @GetMapping("/me")
    public MeResponse me(Authentication authentication) {

        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow();

        MeResponse.MeResponseBuilder builder = MeResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .role(user.getRole().name());

        if (user.getEmployee() != null) {
            builder
                    .employeeId(user.getEmployee().getId())
                    .firstName(user.getEmployee().getFirstName())
                    .lastName(user.getEmployee().getLastName())
                    .profilePhoto(user.getEmployee().getProfilePhoto());
        }

        return builder.build();
    }

    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(
            Authentication authentication,
            @Valid @RequestBody ChangePasswordRequest request) {

        userService.changePassword(
                authentication.getName(),
                request.getCurrentPassword(),
                request.getNewPassword());
    }
}
