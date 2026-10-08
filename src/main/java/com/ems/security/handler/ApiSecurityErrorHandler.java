package com.ems.security.handler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * Returns the same JSON error shape as {@code ApiError} for 401 and 403
 * responses produced by the API security chain.
 */
@Component
public class ApiSecurityErrorHandler
        implements AuthenticationEntryPoint, AccessDeniedHandler {

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException {

        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");

        write(response, HttpStatus.UNAUTHORIZED,
                "Authentication is required to access this resource.");
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {

        write(response, HttpStatus.FORBIDDEN,
                "You do not have permission to perform this action.");
    }

    private void write(
            HttpServletResponse response,
            HttpStatus status,
            String message) throws IOException {

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        // The message is a constant, so no JSON escaping is needed.
        response.getWriter().write(
                "{\"success\":false,\"message\":\"" + message
                        + "\",\"timestamp\":\"" + LocalDateTime.now() + "\"}");
    }
}