package com.ems.gateway.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.util.List;

/**
 * Validates the exact-origin CORS settings consumed by Spring Cloud Gateway's
 * native WebFlux global CORS handler. CORS response handling remains in the
 * gateway framework rather than a second custom CorsWebFilter.
 */
@Configuration
public class GatewayCorsConfiguration {

    @Value("${ems.gateway.cors.allowed-origins:http://localhost:3000,http://localhost:5173}")
    private List<String> allowedOrigins;

    @Value("${ems.gateway.cors.allowed-methods:GET,POST,PUT,DELETE,OPTIONS}")
    private List<String> allowedMethods;

    @Value("${ems.gateway.cors.max-age:3600}")
    private long maxAgeSeconds;

    /**
     * Fails startup for unsafe or malformed CORS settings.
     */
    @PostConstruct
    void validateConfiguration() {
        validateOrigins(allowedOrigins);
        if (allowedMethods == null || allowedMethods.isEmpty()
                || !allowedMethods.contains("OPTIONS")) {
            throw new IllegalStateException("EMS gateway CORS methods must include OPTIONS.");
        }
        if (maxAgeSeconds < 0) {
            throw new IllegalStateException("EMS gateway CORS max age must not be negative.");
        }
    }

    /**
     * Validates a list of exact HTTP(S) origins; wildcard and path-bearing
     * values are intentionally rejected.
     *
     * @param origins configured origins
     */
    void validateOrigins(List<String> origins) {
        if (origins == null || origins.isEmpty()) {
            throw new IllegalStateException("At least one EMS gateway CORS origin must be configured.");
        }

        for (String configuredOrigin : origins) {
            if (configuredOrigin == null || configuredOrigin.isBlank()
                    || "*".equals(configuredOrigin.trim())) {
                throw new IllegalStateException(
                        "EMS gateway CORS origins must be explicit; wildcard origins are not allowed.");
            }

            final URI origin;
            try {
                origin = URI.create(configuredOrigin.trim());
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("Invalid EMS gateway CORS origin.", exception);
            }

            boolean validScheme = "http".equalsIgnoreCase(origin.getScheme())
                    || "https".equalsIgnoreCase(origin.getScheme());
            if (!validScheme || origin.getHost() == null || origin.getUserInfo() != null
                    || origin.getPath() != null && !origin.getPath().isEmpty()
                    || origin.getQuery() != null || origin.getFragment() != null) {
                throw new IllegalStateException(
                        "EMS gateway CORS values must be exact HTTP(S) origins without paths or credentials.");
            }
        }
    }
}
