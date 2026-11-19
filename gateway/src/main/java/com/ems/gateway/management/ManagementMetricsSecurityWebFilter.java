package com.ems.gateway.management;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Protects Actuator metrics with a dedicated management key. Liveness,
 * readiness and the deliberately minimal info endpoint remain available to
 * local probes; operational measurements require explicit authorization.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class ManagementMetricsSecurityWebFilter implements WebFilter {

    private static final String MANAGEMENT_KEY_HEADER = "X-EMS-Management-Key";

    private final byte[] configuredKey;

    /**
     * Creates the management metrics guard.
     *
     * @param managementApiKey environment-provided key for operational metrics
     */
    public ManagementMetricsSecurityWebFilter(
            @Value("${EMS_MANAGEMENT_API_KEY:}") String managementApiKey) {
        this.configuredKey = managementApiKey == null
                ? new byte[0]
                : managementApiKey.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Requires the management key for the metrics endpoint and every metric
     * detail path. An unset key intentionally denies access.
     *
     * @param exchange current HTTP exchange
     * @param chain remaining WebFilter chain
     * @return downstream completion or an unauthorized response
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (!isMetricsPath(path)) {
            return chain.filter(exchange);
        }

        String supplied = exchange.getRequest().getHeaders().getFirst(MANAGEMENT_KEY_HEADER);
        if (!isAuthorized(supplied)) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            exchange.getResponse().getHeaders().setCacheControl("no-store");
            return exchange.getResponse().setComplete();
        }
        return chain.filter(exchange);
    }

    private boolean isMetricsPath(String path) {
        return "/actuator/metrics".equals(path) || path.startsWith("/actuator/metrics/");
    }

    private boolean isAuthorized(String supplied) {
        if (configuredKey.length == 0 || supplied == null || supplied.isBlank()) {
            return false;
        }
        byte[] suppliedKey = supplied.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(configuredKey, suppliedKey);
    }
}
