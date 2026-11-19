package com.ems.gateway.config;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Adds baseline browser security headers before CORS and gateway routing.
 *
 * <p>Running as an outer WebFilter ensures the headers are present on successful
 * responses, CORS preflight responses and gateway-generated 401/403 responses.
 * Existing downstream values are preserved.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecureResponseHeadersWebFilter implements WebFilter {

    private static final String CONTENT_SECURITY_POLICY =
            "default-src 'none'; frame-ancestors 'none'; base-uri 'none'";

    /**
     * Registers security headers before the response is committed.
     *
     * @param exchange current server exchange
     * @param chain remaining WebFilter chain
     * @return downstream response completion
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        exchange.getResponse().beforeCommit(() -> {
            HttpHeaders headers = exchange.getResponse().getHeaders();
            if (exchange.getResponse().getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                // The fastest configured token refill interval is one second.
                setIfAbsent(headers, "Retry-After", "1");
            }
            setIfAbsent(headers, "X-Content-Type-Options", "nosniff");
            setIfAbsent(headers, "X-Frame-Options", "DENY");
            setIfAbsent(headers, "Referrer-Policy", "strict-origin-when-cross-origin");
            setIfAbsent(headers, "Permissions-Policy",
                    "camera=(), geolocation=(), microphone=()");
            setIfAbsent(headers, "X-Permitted-Cross-Domain-Policies", "none");
            setIfAbsent(headers, "Content-Security-Policy", CONTENT_SECURITY_POLICY);
            // Browsers accept HSTS only when the response is received over HTTPS.
            // Setting it here also works when TLS terminates at a trusted edge proxy.
            setIfAbsent(headers, "Strict-Transport-Security",
                    "max-age=31536000; includeSubDomains");
            return Mono.empty();
        });
        return chain.filter(exchange);
    }

    private void setIfAbsent(HttpHeaders headers, String name, String value) {
        if (headers.getFirst(name) == null) {
            headers.set(name, value);
        }
    }
}
