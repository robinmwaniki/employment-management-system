package com.ems.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies gateway CORS allowlisting and browser security response headers.
 */
class GatewayCorsAndSecurityHeadersTests {

    private static final List<String> ALLOWED_ORIGINS =
            List.of("http://localhost:3000", "https://ems.example.test");

    private final GatewayCorsConfiguration corsConfiguration = new GatewayCorsConfiguration();

    @Test
    void allowsConfiguredOriginAndExposesCorrelationId() {
        CorsWebFilter filter = corsFilter(ALLOWED_ORIGINS);
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/employees")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .build());
        AtomicBoolean forwarded = new AtomicBoolean();

        filter.filter(exchange, ignored -> {
            forwarded.set(true);
            return Mono.empty();
        }).block();

        assertThat(forwarded).isTrue();
        assertThat(exchange.getResponse().getHeaders().getFirst(
                HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isEqualTo("http://localhost:3000");
        assertThat(exchange.getResponse().getHeaders().getAccessControlAllowCredentials()).isTrue();
        assertThat(exchange.getResponse().getHeaders().getAccessControlExposeHeaders())
                .contains("X-Correlation-ID");
    }

    @Test
    void rejectsDisallowedOriginDuringPreflight() {
        CorsWebFilter filter = corsFilter(ALLOWED_ORIGINS);
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.options("/api/employees")
                        .header(HttpHeaders.ORIGIN, "https://attacker.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization")
                        .build());
        AtomicBoolean forwarded = new AtomicBoolean();

        filter.filter(exchange, ignored -> {
            forwarded.set(true);
            return Mono.empty();
        }).block();

        assertThat(forwarded).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(exchange.getResponse().getHeaders().getFirst(
                HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();
    }

    @Test
    void answersAllowedPreflightAtGatewayWithoutForwarding() {
        CorsWebFilter filter = corsFilter(ALLOWED_ORIGINS);
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.options("/api/not-routed")
                        .header(HttpHeaders.ORIGIN, "https://ems.example.test")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PATCH")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                                "Authorization,Content-Type,X-Correlation-ID")
                        .build());
        AtomicBoolean forwarded = new AtomicBoolean();

        filter.filter(exchange, ignored -> {
            forwarded.set(true);
            return Mono.empty();
        }).block();

        assertThat(forwarded).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(exchange.getResponse().getHeaders().getFirst(
                HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isEqualTo("https://ems.example.test");
        assertThat(exchange.getResponse().getHeaders().getAccessControlAllowMethods())
                .contains(org.springframework.http.HttpMethod.PATCH);
    }

    @Test
    void rejectsWildcardOriginsWhenCredentialsAreEnabled() {
        assertThatThrownBy(() -> corsFilter(List.of("*")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("wildcard");
    }

    @Test
    void rejectsOriginValuesContainingPathsOrCredentials() {
        assertThatThrownBy(() -> corsFilter(List.of("https://user:pass@ems.example.test/path")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exact HTTP(S) origins");
    }

    @Test
    void addsBaselineSecurityHeadersAndPreservesDownstreamValues() {
        SecureResponseHeadersGlobalFilter filter = new SecureResponseHeadersGlobalFilter();
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/employees").build());
        exchange.getResponse().getHeaders().set("X-Frame-Options", "SAMEORIGIN");

        filter.filter(exchange, ignored -> exchange.getResponse().setComplete()).block();

        HttpHeaders headers = exchange.getResponse().getHeaders();
        assertThat(headers.getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(headers.getFirst("X-Frame-Options")).isEqualTo("SAMEORIGIN");
        assertThat(headers.getFirst("Referrer-Policy"))
                .isEqualTo("strict-origin-when-cross-origin");
        assertThat(headers.getFirst("Permissions-Policy"))
                .contains("camera=()", "geolocation=()", "microphone=()");
        assertThat(headers.getFirst("Content-Security-Policy"))
                .isEqualTo("default-src 'none'; frame-ancestors 'none'; base-uri 'none'");
    }

    private CorsWebFilter corsFilter(List<String> origins) {
        return corsConfiguration.gatewayCorsWebFilter(
                origins,
                List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"),
                List.of("Authorization", "Content-Type", "Accept", "X-Correlation-ID"),
                List.of("X-Correlation-ID"),
                3600);
    }
}
