package com.ems.gateway.resilience;

import com.ems.gateway.error.GatewayErrorResponseWriter;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import io.netty.handler.timeout.ReadTimeoutException;

import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;

/**
 * Produces safe, correlation-aware responses when a route circuit breaker opens
 * or an upstream operation fails.
 */
@RestController
public class GatewayFallbackController {

    private final GatewayErrorResponseWriter errorResponseWriter;

    /**
     * Creates the gateway fallback controller.
     *
     * @param errorResponseWriter standard gateway JSON error writer
     */
    public GatewayFallbackController(GatewayErrorResponseWriter errorResponseWriter) {
        this.errorResponseWriter = errorResponseWriter;
    }

    /**
     * Handles internal circuit-breaker forwards without exposing exception details.
     *
     * @param exchange current fallback exchange
     * @return safe 503 or 504 response completion
     */
    @RequestMapping("/gateway/fallback")
    public Mono<Void> fallback(ServerWebExchange exchange) {
        Throwable failure = exchange.getAttribute(
                ServerWebExchangeUtils.CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR);
        if (isTimeout(failure)) {
            return errorResponseWriter.write(
                    exchange,
                    HttpStatus.GATEWAY_TIMEOUT,
                    "UPSTREAM_TIMEOUT",
                    "The upstream service did not respond in time.");
        }
        return errorResponseWriter.write(
                exchange,
                HttpStatus.SERVICE_UNAVAILABLE,
                "UPSTREAM_UNAVAILABLE",
                "The upstream service is temporarily unavailable.");
    }

    private boolean isTimeout(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof TimeoutException || current instanceof SocketTimeoutException
                    || current instanceof ReadTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
