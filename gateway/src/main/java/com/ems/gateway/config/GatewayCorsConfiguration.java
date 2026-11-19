package com.ems.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.net.URI;
import java.util.List;

/**
 * Defines the gateway's browser-facing CORS policy.
 *
 * <p>CORS is enforced at the gateway edge so browser preflight requests are
 * answered consistently without depending on downstream route matching. The
 * existing backend CORS policy remains available for clients that call it
 * directly; gateway and backend origin allowlists must be kept aligned.</p>
 */
@Configuration
public class GatewayCorsConfiguration {

    /**
     * Creates a restrictive CORS filter using exact, environment-configured origins.
     *
     * @param allowedOrigins exact browser origins allowed to call the gateway
     * @param allowedMethods HTTP methods exposed to browser clients
     * @param allowedHeaders request headers browser clients may send
     * @param exposedHeaders response headers browser clients may read
     * @param maxAgeSeconds preflight cache duration in seconds
     * @return configured reactive CORS filter
     */
    @Bean
    public CorsWebFilter gatewayCorsWebFilter(
            @Value("${ems.gateway.cors.allowed-origins:http://localhost:3000,http://localhost:5173}")
            List<String> allowedOrigins,
            @Value("${ems.gateway.cors.allowed-methods:GET,POST,PUT,PATCH,DELETE,OPTIONS}")
            List<String> allowedMethods,
            @Value("${ems.gateway.cors.allowed-headers:Authorization,Content-Type,Accept,X-Correlation-ID}")
            List<String> allowedHeaders,
            @Value("${ems.gateway.cors.exposed-headers:X-Correlation-ID}")
            List<String> exposedHeaders,
            @Value("${ems.gateway.cors.max-age:3600}") long maxAgeSeconds) {

        validateOrigins(allowedOrigins);
        if (maxAgeSeconds < 0) {
            throw new IllegalStateException("EMS gateway CORS max age must not be negative.");
        }

        CorsConfiguration cors = new CorsConfiguration();
        // Exact patterns keep the configured values strict while supporting credentialed CORS.\n        cors.setAllowedOriginPatterns(allowedOrigins);
        cors.setAllowedMethods(allowedMethods);
        cors.setAllowedHeaders(allowedHeaders);
        cors.setExposedHeaders(exposedHeaders);
        cors.setAllowCredentials(true);
        cors.setMaxAge(maxAgeSeconds);
        cors.validateAllowCredentials();

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return new CorsWebFilter(source);
    }

    private void validateOrigins(List<String> origins) {
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
