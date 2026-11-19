package com.ems.gateway.resilience;

import com.ems.gateway.error.GatewayErrorResponseWriter;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;

import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies safe fallback status mapping and correlation propagation. */
class GatewayFallbackControllerTests {

    private static final String CORRELATION_ID =
            "d2f3c4b5-a697-4810-9234-56789abcdef0";

    private final GatewayFallbackController controller =
            new GatewayFallbackController(
                    new GatewayErrorResponseWriter(JsonMapper.builder().build()));

    @Test
    void returnsSafeUnavailableResponseWithCorrelationId() {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/gateway/fallback")
                        .header("X-Correlation-ID", CORRELATION_ID)
                        .build());

        controller.fallback(exchange).block();

        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(((MockServerHttpResponse) exchange.getResponse())
                .getBodyAsString().block())
                .contains("\"UPSTREAM_UNAVAILABLE\"")
                .contains(CORRELATION_ID)
                .doesNotContain("stackTrace", "exception");
    }

    @Test
    void mapsTimeoutFailuresToGatewayTimeoutWithoutLeakingDetails() {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/gateway/fallback").build());
        exchange.getAttributes().put(
                ServerWebExchangeUtils.CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR,
                new TimeoutException("private upstream endpoint detail"));

        controller.fallback(exchange).block();

        String body = ((MockServerHttpResponse) exchange.getResponse())
                .getBodyAsString().block();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(body).contains("\"UPSTREAM_TIMEOUT\"")
                .doesNotContain("private upstream endpoint detail");
    }
}
