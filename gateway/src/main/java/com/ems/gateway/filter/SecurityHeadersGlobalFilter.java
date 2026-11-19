package com.ems.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Applies a conservative set of browser security headers to gateway responses.
 *
 * <p>The gateway exposes JSON APIs rather than HTML pages, so framing and active
 * content are disabled by default. Existing values are retained when a
 * downstream service deliberately supplies a more specific policy.</p>
 */
@Component
public class SecurityHeadersGlobalFilter implements GlobalFilter, Ordered {

    /**
     * Registers response headers before the response is committed.
     *
     * @param exchange current gateway exchange
     * @param chain remaining gateway filter chain
     * @return completion signal for the filtered request
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        exchange.getResponse().beforeCommit(() -> {
            HttpHeaders headers = exchange.getResponse().getHeaders();
            headers.putIfAbsent("X-Content-Type-Options", java.util.List.of("nosniff"));
            headers.putIfAbsent("X-Frame-Options", java.util.List.of("DENY"));
            headers.putIfAbsent("Referrer-Policy",
                    java.util.List.of("strict-origin-when-cross-origin"));
            headers.putIfAbsent("X-Permitted-Cross-Domain-Policies", java.util.List.of("none"));
            headers.putIfAbsent("Permissions-Policy",
                    java.util.List.of("camera=(), microphone=(), geolocation=()"));
            headers.putIfAbsent("Content-Security-Policy",
                    java.util.List.of("default-src 'none'; frame-ancestors 'none'; "
                            + "base-uri 'none'; object-src 'none'"));
            return Mono.empty();
        });
        return chain.filter(exchange);
    }

    /**
     * Runs after correlation handling and before authentication/route filters.
     *
     * @return filter order
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 5;
    }
}
