package com.ems.gateway.ratelimit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Adds a configurable Retry-After header to rate-limited responses.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RateLimitRetryAfterWebFilter implements WebFilter {

    private final String retryAfterSeconds;

    /**
     * Creates the filter with the client retry interval.
     *
     * @param retryAfterSeconds configured seconds to wait before retrying
     */
    public RateLimitRetryAfterWebFilter(
            @Value("${ems.gateway.rate-limit.retry-after-seconds:5}") String retryAfterSeconds) {
        try {
            int seconds = Integer.parseInt(retryAfterSeconds);
            if (seconds < 1 || seconds > 3600) {
                throw new IllegalArgumentException("Retry-After must be between 1 and 3600 seconds.");
            }
            this.retryAfterSeconds = String.valueOf(seconds);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Retry-After must be a whole number of seconds.", exception);
        }
    }

    /**
     * Adds Retry-After only when the response status is 429.
     *
     * @param exchange current exchange
     * @param chain remaining filter chain
     * @return response completion signal
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        exchange.getResponse().beforeCommit(() -> {
            if (exchange.getResponse().getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                exchange.getResponse().getHeaders().set("Retry-After", retryAfterSeconds);
            }
            return Mono.empty();
        });
        return chain.filter(exchange);
    }
}
