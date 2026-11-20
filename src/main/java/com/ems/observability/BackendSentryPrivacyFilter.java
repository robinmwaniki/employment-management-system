package com.ems.observability;

import io.sentry.Hint;
import io.sentry.SentryEvent;
import io.sentry.SentryOptions;
import io.sentry.protocol.SentryException;
import io.sentry.protocol.SentryStackFrame;
import io.sentry.protocol.SentryStackTrace;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Removes request, user, breadcrumb, tag, context and exception-message data
 * before an event is transmitted. Exception types and source frames remain.
 */
@Component
public class BackendSentryPrivacyFilter implements SentryOptions.BeforeSendCallback {

    @Override
    public SentryEvent execute(SentryEvent event, Hint hint) {
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
        event.setDebugMeta(null);
        event.getContexts().clear();

        List<SentryException> exceptions = event.getExceptions();
        if (exceptions != null) {
            exceptions.forEach(this::sanitizeException);
        }
        return event;
    }

    private void sanitizeException(SentryException exception) {
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
                frame.setAbsPath(null);
            }
        }
    }
}
