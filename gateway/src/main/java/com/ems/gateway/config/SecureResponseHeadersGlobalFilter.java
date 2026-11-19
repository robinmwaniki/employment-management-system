package com.ems.gateway.config;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Adds baseline browser security headers to gateway API responses.
 *
 * <p>Headers already supplied by the downstream application are preserved.
 * HSTS is emitted only when the gateway itself receives an HTTPS request.</p>
 */
@Component
public class SecureResponseHeadersGlobalFilter implements GlobalFilter, Ordered {

    private static final String CONTENT_SECURITY_POLICY =
            "default-src 'none'; frame-ancestors 'none'; base-uri 'none'";

    /**
     * Registers security headers before the response is committed.
     *
     * @param exchange current gateway exchange
     * @param chain remaining gateway filter chain
     * @return downstream response completion
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        exchange.getResponse().beforeCommit(() -> {
            HttpHeaders headers = exchange.getResponse().getHeaders();
            setIfAbsent(headers, "X-Content-Type-Options", "nosniff");
            setIfAbsent(headers, "X-Frame-Options", "DENY");
            setIfAbsent(headers, "Referrer-Policy", "strict-origin-when-cross-origin");
            setIfAbsent(headers, "Permissions-Policy",
                    "camera=(), geolocation=(), microphone=()");
            setIfAbsent(headers, "X-Permitted-Cross-Domain-Policies", "none");
            setIfAbsent(headers, "Content-Security-Policy", CONTENT_SECURITY_POLICY);

            if ("https".equalsIgnoreCase(exchange.getRequest().getURI().getScheme())) {
                setIfAbsent(headers, "Strict-Transport-Security", "max-age=31536000; includeSubDomains");
            }
            return Mono.empty();
        });
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    private void setIfAbsent(HttpHeaders headers, String name, String value) {
        if (headers.getFirst(name) == null) {
            headers.set(name, value);
        }
    }
}
