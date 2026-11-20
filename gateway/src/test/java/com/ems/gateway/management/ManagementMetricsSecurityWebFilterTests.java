package com.ems.gateway.management;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies that operational metrics require the dedicated management key. */
class ManagementMetricsSecurityWebFilterTests {

    @Test
    void rejectsMetricsRequestsWithoutTheManagementKey() {
        ManagementMetricsSecurityWebFilter filter =
                new ManagementMetricsSecurityWebFilter("test-management-key");
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/actuator/metrics/ems.gateway.requests").build());
        AtomicBoolean forwarded = new AtomicBoolean();

        filter.filter(exchange, ignored -> {
            forwarded.set(true);
            return Mono.empty();
        }).block();

        assertThat(forwarded).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getCacheControl()).isEqualTo("no-store");
    }

    @Test
    void rejectsIncorrectManagementKey() {
        ManagementMetricsSecurityWebFilter filter =
                new ManagementMetricsSecurityWebFilter("test-management-key");
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/actuator/metrics")
                        .header("X-EMS-Management-Key", "wrong-key")
                        .build());

        filter.filter(exchange, ignored -> Mono.empty()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void forwardsMetricsRequestsWithTheCorrectManagementKey() {
        ManagementMetricsSecurityWebFilter filter =
                new ManagementMetricsSecurityWebFilter("test-management-key");
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/actuator/metrics/ems.gateway.requests")
                        .header("X-EMS-Management-Key", "test-management-key")
                        .build());
        AtomicBoolean forwarded = new AtomicBoolean();

        filter.filter(exchange, ignored -> {
            forwarded.set(true);
            return Mono.empty();
        }).block();

        assertThat(forwarded).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void acceptsBearerManagementKeyForPrometheusScraping() {
        ManagementMetricsSecurityWebFilter filter =
                new ManagementMetricsSecurityWebFilter("test-management-key");
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/actuator/prometheus")
                        .header("Authorization", "Bearer test-management-key")
                        .build());
        AtomicBoolean forwarded = new AtomicBoolean();

        filter.filter(exchange, ignored -> {
            forwarded.set(true);
            return Mono.empty();
        }).block();

        assertThat(forwarded).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void rejectsMalformedBearerManagementCredentials() {
        ManagementMetricsSecurityWebFilter filter =
                new ManagementMetricsSecurityWebFilter("test-management-key");
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/actuator/prometheus")
                        .header("Authorization", "Basic dXNlcjpwYXNz")
                        .build());

        filter.filter(exchange, ignored -> Mono.empty()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void leavesHealthAndInfoProbesAccessibleWithoutTheMetricsKey() {
        ManagementMetricsSecurityWebFilter filter =
                new ManagementMetricsSecurityWebFilter("test-management-key");
        ServerWebExchange health = MockServerWebExchange.from(
                MockServerHttpRequest.get("/actuator/health/liveness").build());
        ServerWebExchange info = MockServerWebExchange.from(
                MockServerHttpRequest.get("/actuator/info").build());
        AtomicBoolean healthForwarded = new AtomicBoolean();
        AtomicBoolean infoForwarded = new AtomicBoolean();

        filter.filter(health, ignored -> {
            healthForwarded.set(true);
            return Mono.empty();
        }).block();
        filter.filter(info, ignored -> {
            infoForwarded.set(true);
            return Mono.empty();
        }).block();

        assertThat(healthForwarded).isTrue();
        assertThat(infoForwarded).isTrue();
    }

    @Test
    void deniesMetricsWhenNoManagementKeyIsConfigured() {
        ManagementMetricsSecurityWebFilter filter = new ManagementMetricsSecurityWebFilter("");
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/actuator/metrics")
                        .header("X-EMS-Management-Key", "any-key")
                        .build());

        filter.filter(exchange, ignored -> Mono.empty()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
