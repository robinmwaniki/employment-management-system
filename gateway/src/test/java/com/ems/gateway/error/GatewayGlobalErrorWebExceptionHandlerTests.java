package com.ems.gateway.error;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;

import java.net.ConnectException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies stable status mapping and privacy-safe gateway error payloads.
 */
class GatewayGlobalErrorWebExceptionHandlerTests {

    private static final String CORRELATION_ID =
            "c2d5a0a1-94e7-4f8f-a8c9-10dcb13e7412";

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final GatewayGlobalErrorWebExceptionHandler handler =
            new GatewayGlobalErrorWebExceptionHandler(
                    new GatewayErrorResponseWriter(jsonMapper));

    @Test
    void mapsCommonClientErrorsToStableCodes() throws Exception {
        assertError(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "private detail"),
                HttpStatus.UNAUTHORIZED, "GATEWAY_UNAUTHORIZED");
        assertError(new ResponseStatusException(HttpStatus.FORBIDDEN, "private detail"),
                HttpStatus.FORBIDDEN, "GATEWAY_FORBIDDEN");
        assertError(new ResponseStatusException(HttpStatus.NOT_FOUND, "private detail"),
                HttpStatus.NOT_FOUND, "GATEWAY_NOT_FOUND");
        assertError(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "private detail"),
                HttpStatus.TOO_MANY_REQUESTS, "GATEWAY_RATE_LIMITED");
    }

    @Test
    void mapsConnectionFailuresToBadGateway() throws Exception {
        assertError(new ConnectException("internal-host:5432 connection refused"),
                HttpStatus.BAD_GATEWAY, "UPSTREAM_BAD_GATEWAY");
    }

    @Test
    void mapsRejectedWorkToServiceUnavailable() throws Exception {
        assertError(new RejectedExecutionException("internal executor details"),
                HttpStatus.SERVICE_UNAVAILABLE, "UPSTREAM_UNAVAILABLE");
    }

    @Test
    void mapsTimeoutsToGatewayTimeout() throws Exception {
        assertError(new TimeoutException("internal backend timeout"),
                HttpStatus.GATEWAY_TIMEOUT, "UPSTREAM_TIMEOUT");
    }

    @Test
    void hidesUnexpectedExceptionDetails() throws Exception {
        assertError(new IllegalStateException("password=do-not-return"),
                HttpStatus.INTERNAL_SERVER_ERROR, "GATEWAY_ERROR");
    }

    private void assertError(Throwable exception, HttpStatus expectedStatus, String expectedCode)
            throws Exception {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/private")
                        .header("X-Correlation-ID", CORRELATION_ID)
                        .build());

        handler.handle(exchange, exception).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(expectedStatus);
        assertThat(exchange.getResponse().getHeaders().getContentType())
                .isEqualTo(org.springframework.http.MediaType.APPLICATION_JSON);
        assertThat(exchange.getResponse().getHeaders().getCacheControl()).isEqualTo("no-store");

        String json = exchange.getResponse().getBodyAsString().block();
        JsonNode body = jsonMapper.readTree(json);
        assertThat(body.path("status").asInt()).isEqualTo(expectedStatus.value());
        assertThat(body.path("code").asText()).isEqualTo(expectedCode);
        assertThat(body.path("timestamp").asText()).isNotBlank();
        assertThat(body.path("correlationId").asText()).isEqualTo(CORRELATION_ID);
        assertThat(body.path("message").asText()).doesNotContain(
                "private detail", "internal-host", "password", "executor details");
        assertThat(json).doesNotContain("stackTrace", "exception", "password=do-not-return");
    }
}
