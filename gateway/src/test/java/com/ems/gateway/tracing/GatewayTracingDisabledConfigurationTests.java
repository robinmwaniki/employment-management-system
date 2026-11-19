package com.ems.gateway.tracing;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies that tracing can be disabled without requiring an OTLP collector. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "management.opentelemetry.enabled=false",
                "management.tracing.export.otlp.enabled=false",
                "EMS_RATE_LIMIT_ENABLED=false",
                "JWT_SECRET=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
                "EMS_MANAGEMENT_API_KEY=test-management-key"
        })
class GatewayTracingDisabledConfigurationTests {

    @Autowired
    private OpenTelemetry openTelemetry;

    @Test
    void usesNoopSpansWhenOpenTelemetryIsDisabled() {
        Span span = openTelemetry.getTracer("ems-gateway-test")
                .spanBuilder("disabled-trace")
                .startSpan();

        try {
            assertThat(span.getSpanContext().isValid()).isFalse();
        } finally {
            span.end();
        }
    }
}
