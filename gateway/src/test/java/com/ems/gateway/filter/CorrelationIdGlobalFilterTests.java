package com.ems.gateway.filter;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests correlation ID validation, request propagation and response exposure.
 */
class CorrelationIdGlobalFilterTests {

    private final CorrelationIdGlobalFilter filter =
            new CorrelationIdGlobalFilter();
    private final Logger filterLogger =
            (Logger) LoggerFactory.getLogger(CorrelationIdGlobalFilter.class);
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void captureFilterLogs() {
        logAppender = new ListAppender<>();
        logAppender.start();
        filterLogger.addAppender(logAppender);
    }

    @AfterEach
    void stopCapturingFilterLogs() {
        filterLogger.detachAppender(logAppender);
        logAppender.stop();
    }

    /**
     * Preserves a canonical UUID and makes it available downstream and upstream.
     */
    @Test
    void propagatesValidCorrelationId() {
        String id = "c1d6a9f0-4e8c-4a6b-9c1a-123456789abc";
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/employees")
                        .header(CorrelationIdGlobalFilter.CORRELATION_ID_HEADER, id)
                        .build());
        AtomicReference<String> downstreamId = new AtomicReference<>();

        filter.filter(exchange, filtered -> {
            downstreamId.set(filtered.getRequest().getHeaders()
                    .getFirst(CorrelationIdGlobalFilter.CORRELATION_ID_HEADER));
            return Mono.empty();
        }).block();

        assertThat(downstreamId.get()).isEqualTo(id);
        assertThat(exchange.getResponse().getHeaders()
                .getFirst(CorrelationIdGlobalFilter.CORRELATION_ID_HEADER))
                .isEqualTo(id);
    }

    /**
     * Replaces a malformed client value instead of forwarding it to backend logs.
     */
    @Test
    void replacesMalformedCorrelationId() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/employees")
                        .header(CorrelationIdGlobalFilter.CORRELATION_ID_HEADER,
                                "bad-id\r\nforged-log-entry")
                        .build());
        AtomicReference<String> downstreamId = new AtomicReference<>();

        filter.filter(exchange, filtered -> {
            downstreamId.set(filtered.getRequest().getHeaders()
                    .getFirst(CorrelationIdGlobalFilter.CORRELATION_ID_HEADER));
            return Mono.empty();
        }).block();

        assertThat(UUID.fromString(downstreamId.get())).isNotNull();
        assertThat(downstreamId.get())
                .isNotEqualTo("bad-id\r\nforged-log-entry");
        assertThat(exchange.getResponse().getHeaders()
                .getFirst(CorrelationIdGlobalFilter.CORRELATION_ID_HEADER))
                .isEqualTo(downstreamId.get());
    }

    /**
     * Generates a UUID when the client omits the correlation header.
     */
    @Test
    void generatesCorrelationIdWhenHeaderIsMissing() {
        assertThat(UUID.fromString(
                CorrelationIdGlobalFilter.resolveCorrelationId(null))).isNotNull();
    }

    /**
     * Confirms completion logs contain operational metadata but never request
     * query values, authorization credentials, cookies or employee payloads.
     */
    @Test
    void logsOnlyPrivacySafeRequestMetadata() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/employees?employeeId=private-employee")
                        .header("Authorization", "Bearer sensitive-access-token")
                        .cookie(new HttpCookie("SESSION", "sensitive-session-cookie"))
                        .body("salary=confidential-payload"));

        filter.filter(exchange, filtered -> {
            filtered.getResponse().setStatusCode(HttpStatus.ACCEPTED);
            return Mono.empty();
        }).block();

        String logOutput = logAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.joining("\n"));

        assertThat(logOutput)
                .contains("routeId=unmatched")
                .contains("method=POST")
                .contains("status=202")
                .contains("durationMs=")
                .contains("correlationId=");
        assertThat(logOutput)
                .doesNotContain("private-employee")
                .doesNotContain("sensitive-access-token")
                .doesNotContain("sensitive-session-cookie")
                .doesNotContain("confidential-payload")
                .doesNotContain("Authorization")
                .doesNotContain("SESSION");
    }

}
