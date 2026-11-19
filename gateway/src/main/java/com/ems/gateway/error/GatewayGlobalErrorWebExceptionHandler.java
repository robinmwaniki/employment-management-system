package com.ems.gateway.error;

import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.core.Ordered;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * Converts uncaught gateway failures into stable JSON responses.
 *
 * <p>Only safe status-specific messages are returned. Exception messages,
 * stack traces, backend addresses and credentials are never serialized.</p>
 */
@Component
public class GatewayGlobalErrorWebExceptionHandler
        implements ErrorWebExceptionHandler, Ordered {

    private final GatewayErrorResponseWriter responseWriter;

    /**
     * Creates the global gateway exception handler.
     *
     * @param responseWriter shared JSON error response writer
     */
    public GatewayGlobalErrorWebExceptionHandler(GatewayErrorResponseWriter responseWriter) {
        this.responseWriter = responseWriter;
    }

    /**
     * Maps an uncaught exception to a safe HTTP status and error code.
     *
     * @param exchange current server exchange
     * @param exception uncaught gateway failure
     * @return response completion signal
     */
    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable exception) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(exception);
        }

        HttpStatus status = resolveStatus(exception);
        return responseWriter.write(exchange, status, errorCode(status), safeMessage(status));
    }

    /**
     * Runs before the framework's default reactive error handler.
     *
     * @return handler order
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private HttpStatus resolveStatus(Throwable exception) {
        Throwable cause = rootCause(exception);
        if (hasCause(exception, DataAccessException.class)) {
            // Rate limiting fails closed when Redis is unavailable; do not bypass quotas.
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        if (cause instanceof TimeoutException || cause instanceof SocketTimeoutException) {
            return HttpStatus.GATEWAY_TIMEOUT;
        }
        if (cause instanceof RejectedExecutionException) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        if (cause instanceof ConnectException || cause instanceof UnknownHostException) {
            return HttpStatus.BAD_GATEWAY;
        }
        if (exception instanceof ResponseStatusException responseStatusException) {
            return HttpStatus.valueOf(responseStatusException.getStatusCode().value());
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private boolean hasCause(Throwable exception, Class<? extends Throwable> type) {
        Throwable current = exception;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private Throwable rootCause(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private String errorCode(HttpStatus status) {
        return switch (status) {
            case UNAUTHORIZED -> "GATEWAY_UNAUTHORIZED";
            case FORBIDDEN -> "GATEWAY_FORBIDDEN";
            case NOT_FOUND -> "GATEWAY_NOT_FOUND";
            case TOO_MANY_REQUESTS -> "GATEWAY_RATE_LIMITED";
            case BAD_GATEWAY -> "UPSTREAM_BAD_GATEWAY";
            case SERVICE_UNAVAILABLE -> "UPSTREAM_UNAVAILABLE";
            case GATEWAY_TIMEOUT -> "UPSTREAM_TIMEOUT";
            default -> "GATEWAY_ERROR";
        };
    }

    private String safeMessage(HttpStatus status) {
        return switch (status) {
            case UNAUTHORIZED -> "Authentication is required or the supplied token is invalid.";
            case FORBIDDEN -> "You are not permitted to access this resource.";
            case NOT_FOUND -> "The requested gateway resource was not found.";
            case TOO_MANY_REQUESTS -> "Too many requests. Please try again later.";
            case BAD_GATEWAY -> "The gateway could not complete the request to the upstream service.";
            case SERVICE_UNAVAILABLE -> "The upstream service is temporarily unavailable.";
            case GATEWAY_TIMEOUT -> "The upstream service did not respond in time.";
            default -> "The gateway could not complete the request.";
        };
    }
}
