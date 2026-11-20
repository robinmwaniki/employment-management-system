package com.ems.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.net.URI;
import java.util.List;

/**
 * Defines the gateway's browser-facing CORS policy using exact environment
 * configured origins. The gateway handles preflight requests at the edge;
 * the existing backend CORS policy remains available for direct backend access.
 */
@Configuration
public class GatewayCorsConfiguration {

    /**
     * Creates a restrictive CORS filter using exact HTTP(S) origins.
     *
     * @param allowedOrigins comma-separated origins allowed to call the gateway
     * @param allowedMethods HTTP methods exposed to browser clients
     * @param allowedHeaders request headers browser clients may send
     * @param exposedHeaders response headers browser clients may read
     * @param maxAgeSeconds preflight cache duration in seconds
     * @return configured reactive CORS filter
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 1)
    public CorsWebFilter gatewayCorsWebFilter(
            @Value("${ems.gateway.cors.allowed-origins:http://localhost:3000,http://localhost:5173}")
            List<String> allowedOrigins,
            @Value("${ems.gateway.cors.allowed-methods:GET,POST,PUT,DELETE,OPTIONS}")
            List<String> allowedMethods,
            @Value("${ems.gateway.cors.allowed-headers:Authorization,Content-Type,Accept,X-Correlation-ID}")
            List<String> allowedHeaders,
            @Value("${ems.gateway.cors.exposed-headers:Authorization,X-Correlation-ID}")
            List<String> exposedHeaders,
            @Value("${ems.gateway.cors.max-age:3600}") long maxAgeSeconds) {

        List<String> normalizedOrigins = allowedOrigins == null
                ? List.of()
                : allowedOrigins.stream()
                        .filter(origin -> origin != null && !origin.isBlank())
                        .map(String::trim)
                        .distinct()
                        .toList();

        validateOrigins(normalizedOrigins);
        if (maxAgeSeconds < 0) {
            throw new IllegalStateException("EMS gateway CORS max age must not be negative.");
        }
        if (allowedMethods == null || allowedMethods.isEmpty()
                || !allowedMethods.contains("OPTIONS")) {
            throw new IllegalStateException("EMS gateway CORS methods must include OPTIONS.");
        }

        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(normalizedOrigins);
        cors.setAllowedMethods(allowedMethods);
        cors.setAllowedHeaders(allowedHeaders);
        cors.setExposedHeaders(exposedHeaders);
        // The EMS React clients use bearer tokens, not cross-origin cookies.
        // Credentials remain disabled to match the backend CORS policy.
        cors.setAllowCredentials(false);
        cors.setMaxAge(maxAgeSeconds);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return new CorsWebFilter(source);
    }

    private void validateOrigins(List<String> origins) {
        if (origins == null || origins.isEmpty()) {
            throw new IllegalStateException("At least one EMS gateway CORS origin must be configured.");
        }

        for (String configuredOrigin : origins) {
            if ("*".equals(configuredOrigin)) {
                throw new IllegalStateException(
                        "EMS gateway CORS origins must be explicit; wildcard origins are not allowed.");
            }

            final URI origin;
            try {
                origin = URI.create(configuredOrigin);
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
