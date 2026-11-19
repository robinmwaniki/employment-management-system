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
        this.retryAfterSeconds = retryAfterSeconds;
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
