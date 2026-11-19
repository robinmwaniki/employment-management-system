package com.ems.gateway.sentry;

import io.sentry.Hint;
import io.sentry.SentryEvent;
import io.sentry.SentryOptions;
import io.sentry.protocol.SentryException;
import io.sentry.protocol.SentryStackFrame;
import io.sentry.protocol.SentryStackTrace;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Applies privacy controls and noise filtering before gateway events leave the process.
 *
 * <p>Request/user context and arbitrary event metadata are deliberately removed.
 * Exception types and stack frames remain useful for diagnosis, while message values
 * and local-variable snapshots are discarded because they may contain employee data.</p>
 */
@Component
public class GatewaySentryPrivacyFilter implements SentryOptions.BeforeSendCallback {

    /**
     * Drops handled client-side failures and sanitizes all retained server-side events.
     *
     * @param event event prepared by the Sentry SDK
     * @param hint SDK event hint
     * @return sanitized event, or {@code null} when the failure is an expected client error
     */
    @Override
    public SentryEvent execute(SentryEvent event, Hint hint) {
        if (isHandledClientError(event.getThrowable())) {
            return null;
        }

        event.setRequest(null);
        event.setUser(null);
        event.setBreadcrumbs(List.of());
        event.setExtras(Map.of());
        event.setTags(Map.of());
        event.setTransaction(null);
        event.setServerName(null);
        event.setMessage(null);
        event.setThreads(List.of());
        event.setUnknown(null);
        event.getContexts().clear();

        List<SentryException> exceptions = event.getExceptions();
        if (exceptions != null) {
            exceptions.forEach(this::sanitizeException);
        }

        return event;
    }

    private boolean isHandledClientError(Throwable throwable) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = throwable;

        while (current != null && visited.add(current)) {
            if (current instanceof ResponseStatusException responseStatus
                    && responseStatus.getStatusCode().is4xxClientError()) {
                return true;
            }
            if (current instanceof WebClientResponseException webClientResponse
                    && webClientResponse.getStatusCode().is4xxClientError()) {
                return true;
            }
            if (current instanceof AccessDeniedException
                    || current instanceof AuthenticationException) {
                return true;
            }
            current = current.getCause();
        }

        return false;
    }

    private void sanitizeException(SentryException exception) {
        // Preserve exception type and source frames, but never transmit arbitrary messages.
        exception.setValue(null);
        exception.setUnknown(null);

        SentryStackTrace stackTrace = exception.getStacktrace();
        if (stackTrace == null) {
            return;
        }

        stackTrace.setRegisters(null);
        if (stackTrace.getFrames() != null) {
            for (SentryStackFrame frame : stackTrace.getFrames()) {
                frame.setVars(null);
            }
        }
    }
}
