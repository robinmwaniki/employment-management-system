package com.ems.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies CORS configuration validation and browser security headers on
 * successful and gateway-generated error responses.
 */
class GatewayCorsAndSecurityHeadersTests {

    private static final List<String> ALLOWED_ORIGINS =
            List.of("http://localhost:3000", "https://ems.example.test");

    private final GatewayCorsConfiguration corsConfiguration = new GatewayCorsConfiguration();

    @Test
    void acceptsMultipleExactHttpAndHttpsOrigins() {
        corsConfiguration.validateOrigins(ALLOWED_ORIGINS);
    }

    @Test
    void rejectsWildcardOrigins() {
        assertThatThrownBy(() -> corsConfiguration.validateOrigins(List.of("*")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("wildcard");
    }

    @Test
    void rejectsOriginValuesContainingPathsOrCredentials() {
        assertThatThrownBy(() -> corsConfiguration.validateOrigins(
                List.of("https://user:pass@ems.example.test/path")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exact HTTP(S) origins");
    }

    @Test
    void rejectsAnEmptyOriginAllowlist() {
        assertThatThrownBy(() -> corsConfiguration.validateOrigins(List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("At least one");
    }

    @Test
    void addsBaselineSecurityHeadersAndPreservesDownstreamValues() {
        SecureResponseHeadersWebFilter filter = new SecureResponseHeadersWebFilter();
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/employees").build());
        exchange.getResponse().getHeaders().set("X-Frame-Options", "SAMEORIGIN");

        filter.filter(exchange, ignored -> {
            exchange.getResponse().setStatusCode(HttpStatus.ACCEPTED);
            return exchange.getResponse().setComplete();
        }).block();

        assertSecurityHeaders(exchange);
        assertThat(exchange.getResponse().getHeaders().getFirst("X-Frame-Options"))
                .isEqualTo("SAMEORIGIN");
    }

    @Test
    void addsSecurityHeadersToGatewayUnauthorizedResponses() {
        SecureResponseHeadersWebFilter filter = new SecureResponseHeadersWebFilter();
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/employees").build());

        filter.filter(exchange, ignored -> {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertSecurityHeaders(exchange);
    }

    private void assertSecurityHeaders(ServerWebExchange exchange) {
        HttpHeaders headers = exchange.getResponse().getHeaders();
        assertThat(headers.getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(headers.getFirst("X-Frame-Options")).isNotBlank();
        assertThat(headers.getFirst("Referrer-Policy"))
                .isEqualTo("strict-origin-when-cross-origin");
        assertThat(headers.getFirst("Permissions-Policy"))
                .contains("camera=()", "geolocation=()", "microphone=()");
        assertThat(headers.getFirst("X-Permitted-Cross-Domain-Policies")).isEqualTo("none");
        assertThat(headers.getFirst("Content-Security-Policy"))
                .isEqualTo("default-src 'none'; frame-ancestors 'none'; base-uri 'none'");
        assertThat(headers.getFirst("Strict-Transport-Security"))
                .isEqualTo("max-age=31536000; includeSubDomains");
    }
}
