package com.ems.observability;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Establishes safe request correlation metadata and emits body-free completion logs.
 * The gateway's X-Correlation-ID is preserved only when it is a canonical UUID.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RequestObservabilityFilter extends OncePerRequestFilter implements Ordered {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String TRACEPARENT_HEADER = "traceparent";
    private static final Pattern TRACEPARENT_PATTERN = Pattern.compile(
            "^[0-9a-f]{2}-([0-9a-f]{32})-[0-9a-f]{16}-[0-9a-f]{2}$",
            Pattern.CASE_INSENSITIVE);

    private final Tracer tracer;

    @Override
    public int getOrder() {
        // Run inside the server observation filter so the active span is available.
        return Ordered.LOWEST_PRECEDENCE - 10;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String correlationId = resolveCorrelationId(
                request.getHeader(CORRELATION_ID_HEADER));
        String traceId = resolveTraceId(request, tracer);

        response.setHeader(CORRELATION_ID_HEADER, correlationId);

        long startedAt = System.nanoTime();
        try (MDC.MDCCloseable correlation = MDC.putCloseable("correlationId", correlationId);
             MDC.MDCCloseable trace = traceId == null
                     ? MDC.putCloseable("traceId", "")
                     : MDC.putCloseable("traceId", traceId)) {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            log.info("http_request method={} status={} durationMs={} correlationId={} traceId={}",
                    request.getMethod(),
                    response.getStatus(),
                    durationMs,
                    correlationId,
                    traceId == null ? "none" : traceId);
        }
    }

    static String resolveCorrelationId(String candidate) {
        if (candidate != null) {
            try {
                UUID parsed = UUID.fromString(candidate);
                if (parsed.toString().equalsIgnoreCase(candidate)) {
                    return parsed.toString();
                }
            } catch (IllegalArgumentException ignored) {
                // Never forward untrusted or malformed correlation values.
            }
        }
        return UUID.randomUUID().toString();
    }

    static String resolveTraceId(HttpServletRequest request, Tracer tracer) {
        Span current = tracer == null ? null : tracer.currentSpan();
        if (current != null && current.context() != null) {
            String traceId = current.context().traceId();
            if (isValidTraceId(traceId)) {
                return traceId;
            }
        }

        String traceparent = request.getHeader(TRACEPARENT_HEADER);
        if (traceparent == null) {
            return null;
        }

        Matcher matcher = TRACEPARENT_PATTERN.matcher(traceparent.trim());
        if (!matcher.matches()) {
            return null;
        }

        String traceId = matcher.group(1).toLowerCase();
        return isValidTraceId(traceId) ? traceId : null;
    }

    private static boolean isValidTraceId(String traceId) {
        return traceId != null
                && traceId.matches("[0-9a-fA-F]{32}")
                && !traceId.matches("0{32}");
    }
}
