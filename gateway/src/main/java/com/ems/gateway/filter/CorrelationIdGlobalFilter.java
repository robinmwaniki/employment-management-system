package com.ems.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

/**
 * Adds a validated correlation identifier to every gateway request and response.
 *
 * <p>Only canonical UUID values supplied by clients are accepted. Invalid values
 * are replaced to prevent log injection and unbounded identifier formats.</p>
 */
@Component
public class CorrelationIdGlobalFilter implements GlobalFilter, Ordered {

    /** Header used to correlate a request across gateway and backend logs. */
    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    private static final Logger log =
            LoggerFactory.getLogger(CorrelationIdGlobalFilter.class);

    /**
     * Adds the correlation header before routing and records a privacy-safe
     * completion event without logging request bodies, query strings or tokens.
     *
     * @param exchange current server exchange
     * @param chain remaining gateway filter chain
     * @return completion signal for the filtered request
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String correlationId = resolveCorrelationId(
                exchange.getRequest().getHeaders().getFirst(CORRELATION_ID_HEADER));

        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> headers.set(CORRELATION_ID_HEADER, correlationId))
                .build();

        ServerWebExchange correlatedExchange = exchange.mutate()
                .request(request)
                .build();

        correlatedExchange.getResponse().getHeaders()
                .set(CORRELATION_ID_HEADER, correlationId);

        long startedAt = System.nanoTime();

        return chain.filter(correlatedExchange)
                .doFinally(signal -> logCompletion(
                        correlatedExchange, correlationId, startedAt));
    }

    /**
     * Runs early so the identifier is available to downstream gateway filters.
     *
     * @return filter order
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    /**
     * Accepts only canonical UUID strings; otherwise creates a new identifier.
     *
     * @param candidate untrusted incoming header value
     * @return safe correlation identifier
     */
    static String resolveCorrelationId(String candidate) {
        if (candidate != null) {
            try {
                UUID parsed = UUID.fromString(candidate);
                if (parsed.toString().equalsIgnoreCase(candidate)) {
                    return parsed.toString();
                }
            } catch (IllegalArgumentException ignored) {
                // Replace malformed or injected values with a server-generated UUID.
            }
        }
        return UUID.randomUUID().toString();
    }

    private void logCompletion(
            ServerWebExchange exchange, String correlationId, long startedAt) {

        Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
        String routeId = route == null ? "unmatched" : route.getId();
        int status = exchange.getResponse().getStatusCode() == null
                ? 0
                : exchange.getResponse().getStatusCode().value();
        long durationMs = (System.nanoTime() - startedAt) / 1_000_000;

        log.info(
                "gateway_request routeId={} method={} status={} durationMs={} correlationId={}",
                routeId,
                exchange.getRequest().getMethod(),
                status,
                durationMs,
                correlationId);
    }
}
