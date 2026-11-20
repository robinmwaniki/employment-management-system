package com.ems.observability;

import io.sentry.Hint;
import io.sentry.SentryEvent;
import io.sentry.protocol.Request;
import io.sentry.protocol.SentryException;
import io.sentry.protocol.SentryStackFrame;
import io.sentry.protocol.SentryStackTrace;
import io.sentry.protocol.User;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies correlation parsing and removal of sensitive telemetry context.
 */
class BackendObservabilityTests {

    private final BackendSentryPrivacyFilter privacyFilter =
            new BackendSentryPrivacyFilter();

    @Test
    void acceptsCanonicalCorrelationUuidAndReplacesMalformedValues() {
        String id = "c1d6a9f0-4e8c-4a6b-9c1a-123456789abc";

        assertThat(RequestObservabilityFilter.resolveCorrelationId(id)).isEqualTo(id);
        assertThat(UUID.fromString(
                RequestObservabilityFilter.resolveCorrelationId("forged\\r\\nlog-entry")))
                .isNotNull();
        assertThat(RequestObservabilityFilter.resolveCorrelationId(null))
                .isNotBlank();
    }

    @Test
    void extractsOnlyValidW3cTraceIds() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("traceparent",
                "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");

        assertThat(RequestObservabilityFilter.resolveTraceId(request, null))
                .isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");

        request = new MockHttpServletRequest();
        request.addHeader("traceparent",
                "00-00000000000000000000000000000000-00f067aa0ba902b7-01");
        assertThat(RequestObservabilityFilter.resolveTraceId(request, null)).isNull();

        request = new MockHttpServletRequest();
        request.addHeader("traceparent", "malformed");
        assertThat(RequestObservabilityFilter.resolveTraceId(request, null)).isNull();
    }

    @Test
    void stripsSensitiveRequestUserAndExceptionDetailsFromSentryEvent() {
        SentryEvent event = new SentryEvent(
                new IllegalStateException("employee=123 salary=confidential"));

        Request request = new Request();
        request.setUrl("https://ems.example/api/payroll?employeeId=123");
        request.setQueryString("employeeId=123");
        request.setData("salary=confidential");
        request.setCookies("JSESSIONID=session-secret");
        request.setHeaders(Map.of("Authorization", "Bearer jwt-secret"));
        event.setRequest(request);

        User user = new User();
        user.setId("employee-123");
        user.setEmail("employee@example.test");
        event.setUser(user);
        event.setExtras(Map.of("payroll", "confidential"));
        event.setTags(Map.of("employeeId", "123"));
        event.setTransaction("/api/payroll/123");
        event.getContexts().put("custom", "sensitive-context");

        SentryStackFrame frame = new SentryStackFrame();
        frame.setVars(Map.of("password", "local-secret"));
        frame.setAbsPath("/private/source/PayrollService.java");
        SentryStackTrace stackTrace = new SentryStackTrace();
        stackTrace.setFrames(List.of(frame));
        SentryException exception = new SentryException();
        exception.setType("IllegalStateException");
        exception.setValue("employee=123 salary=confidential");
        exception.setStacktrace(stackTrace);
        event.setExceptions(List.of(exception));

        SentryEvent sanitized = privacyFilter.execute(event, new Hint());

        assertThat(sanitized).isSameAs(event);
        assertThat(event.getRequest()).isNull();
        assertThat(event.getUser()).isNull();
        assertThat(event.getExtras()).isEmpty();
        assertThat(event.getTags()).isEmpty();
        assertThat(event.getTransaction()).isNull();
        assertThat(event.getMessage()).isNull();
        assertThat(event.getContexts().get("custom")).isNull();
        assertThat(exception.getType()).isEqualTo("IllegalStateException");
        assertThat(exception.getValue()).isNull();
        assertThat(frame.getVars()).isNull();
        assertThat(frame.getAbsPath()).isNull();
        assertThat(stackTrace.getRegisters()).isNull();
    }
}
