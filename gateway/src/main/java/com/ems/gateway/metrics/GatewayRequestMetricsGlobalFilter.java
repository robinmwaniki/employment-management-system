package com.ems.gateway.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Records bounded-cardinality request counters and latency timers for routed
 * gateway traffic without tagging metrics with paths, query values or identities.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 100)
public class GatewayRequestMetricsGlobalFilter implements GlobalFilter, Ordered {

    private static final String REQUEST_COUNTER = "ems.gateway.requests";
    private static final String REQUEST_TIMER = "ems.gateway.request.duration";
    private static final Set<String> ALLOWED_METHODS =
            Set.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");

    private final MeterRegistry meterRegistry;

    /**
     * Creates the gateway request metrics filter.
     *
     * @param meterRegistry Micrometer registry managed by Spring Boot
     */
    public GatewayRequestMetricsGlobalFilter(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Records a request once the final response status is known. The fallback
     * hooks cover aborted/error exchanges that do not reach response commit.
     *
     * @param exchange current gateway request
     * @param chain remaining gateway filter chain
     * @return downstream completion
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startedAt = System.nanoTime();
        AtomicBoolean recorded = new AtomicBoolean();

        exchange.getResponse().beforeCommit(() -> {
            recordOnce(exchange, startedAt, recorded, null);
            return Mono.empty();
        });

        return chain.filter(exchange)
                .doOnError(error -> recordOnce(exchange, startedAt, recorded, "server_error"))
                .doFinally(signal -> recordOnce(exchange, startedAt, recorded, null));
    }

    /**
     * Runs after the other gateway filters so the selected route ID is available.
     *
     * @return global filter order
     */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 100;
    }

    private void recordOnce(
            ServerWebExchange exchange,
            long startedAt,
            AtomicBoolean recorded,
            String forcedOutcome) {
        if (!recorded.compareAndSet(false, true)) {
            return;
        }

        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        String routeId = route == null ? "unmatched" : route.getId();
        String method = boundedMethod(exchange.getRequest().getMethod());
        String outcome = forcedOutcome == null
                ? outcome(exchange.getResponse().getStatusCode())
                : forcedOutcome;
        long elapsedNanos = Math.max(0, System.nanoTime() - startedAt);

        meterRegistry.counter(
                REQUEST_COUNTER,
                "routeId", routeId,
                "method", method,
                "outcome", outcome).increment();

        Timer.builder(REQUEST_TIMER)
                .description("Gateway request processing duration")
                .tags("routeId", routeId, "method", method, "outcome", outcome)
                .register(meterRegistry)
                .record(elapsedNanos, TimeUnit.NANOSECONDS);
    }

    private String boundedMethod(HttpMethod method) {
        if (method == null) {
            return "UNKNOWN";
        }
        String name = method.name();
        return ALLOWED_METHODS.contains(name) ? name : "OTHER";
    }

    private String outcome(HttpStatusCode status) {
        if (status == null) {
            return "unknown";
        }
        int value = status.value();
        if (value >= 200 && value < 300) {
            return "success";
        }
        if (value >= 300 && value < 400) {
            return "redirect";
        }
        if (value >= 400 && value < 500) {
            return "client_error";
        }
        if (value >= 500 && value < 600) {
            return "server_error";
        }
        return "unknown";
    }
}
