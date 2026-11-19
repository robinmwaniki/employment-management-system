package com.ems.gateway.sentry;

import io.sentry.Breadcrumb;
import io.sentry.Hint;
import io.sentry.SentryEvent;
import io.sentry.protocol.Request;
import io.sentry.protocol.SentryException;
import io.sentry.protocol.SentryStackFrame;
import io.sentry.protocol.SentryStackTrace;
import io.sentry.protocol.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies Sentry drops expected client failures and removes sensitive event context.
 */
class GatewaySentryPrivacyFilterTests {

    private final GatewaySentryPrivacyFilter privacyFilter = new GatewaySentryPrivacyFilter();

    @Test
    void dropsHandledFourHundredExceptions() {
        SentryEvent event = new SentryEvent(
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "email=user@example.test"));

        assertThat(privacyFilter.execute(event, new Hint())).isNull();
    }

    @Test
    void keepsServerFailureButRemovesRequestUserAndArbitraryData() {
        SentryEvent event = new SentryEvent(
                new IllegalStateException("employee=123 password=do-not-send"));

        Request request = new Request();
        request.setUrl("https://gateway.example/api/employees/123?token=secret");
        request.setQueryString("token=secret");
        request.setData("employee personal data");
        request.setCookies("JSESSIONID=session-secret");
        request.setHeaders(Map.of("Authorization", "Bearer jwt-secret"));
        event.setRequest(request);

        User user = new User();
        user.setId("employee-123");
        user.setEmail("employee@example.test");
        event.setUser(user);
        Breadcrumb breadcrumb = new Breadcrumb(System.currentTimeMillis());
        breadcrumb.setMessage("cookie=session-secret");
        event.setBreadcrumbs(List.of(breadcrumb));
        event.setExtras(Map.of("employeeId", "123", "password", "secret"));
        event.setTags(Map.of("username", "employee@example.test"));
        event.setTransaction("/api/employees/123");
        event.setServerName("internal-host-with-user-data");
        event.getContexts().put("custom", "sensitive-context");
        event.setUnknown(Map.of("privateField", "sensitive-value"));

        SentryStackFrame frame = new SentryStackFrame();
        frame.setVars(Map.of("password", "local-secret"));
        frame.setAbsPath("/home/employee/private/project/Source.java");
        SentryStackTrace stackTrace = new SentryStackTrace();
        stackTrace.setFrames(List.of(frame));
        SentryException exception = new SentryException();
        exception.setType("IllegalStateException");
        exception.setValue("employee=123 password=do-not-send");
        exception.setStacktrace(stackTrace);
        event.setExceptions(List.of(exception));

        SentryEvent sanitized = privacyFilter.execute(event, new Hint());

        assertThat(sanitized).isSameAs(event);
        assertThat(event.getRequest()).isNull();
        assertThat(event.getUser()).isNull();
        assertThat(event.getBreadcrumbs()).isEmpty();
        assertThat(event.getExtras()).isEmpty();
        assertThat(event.getTags()).isEmpty();
        assertThat(event.getTransaction()).isNull();
        assertThat(event.getServerName()).isNull();
        assertThat(event.getMessage()).isNull();
        assertThat(event.getThreads()).isEmpty();
        assertThat(event.getUnknown()).isNull();
        assertThat(event.getContexts().get("custom")).isNull();
        assertThat(exception.getType()).isEqualTo("IllegalStateException");
        assertThat(exception.getValue()).isNull();
        assertThat(exception.getUnknown()).isNull();
        assertThat(frame.getVars()).isNull();
        assertThat(frame.getAbsPath()).isNull();
        assertThat(stackTrace.getRegisters()).isNull();
    }

    @Test
    void retainsUnexpectedServerStatusExceptions() {
        SentryEvent event = new SentryEvent(
                new ResponseStatusException(HttpStatus.BAD_GATEWAY, "upstream internal detail"));

        assertThat(privacyFilter.execute(event, new Hint())).isSameAs(event);
    }
}
