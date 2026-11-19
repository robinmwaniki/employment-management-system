package com.ems.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Rejects missing or invalid bearer tokens before protected API requests are
 * forwarded. The existing login endpoint remains public; the backend continues
 * to validate the same token and remains authoritative for account state and
 * role-based authorization.
 */
@Component
public class JwtAuthenticationGlobalFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String PUBLIC_LOGIN_PATH = "/api/auth/login";

    private final JwtTokenValidator tokenValidator;
    private final JwtRouteAuthorizationPolicy authorizationPolicy;

    /**
     * Creates the gateway authentication filter.
     *
     * @param tokenValidator EMS-compatible JWT validator
     */
    public JwtAuthenticationGlobalFilter(
            JwtTokenValidator tokenValidator,
            JwtRouteAuthorizationPolicy authorizationPolicy) {
        this.tokenValidator = tokenValidator;
        this.authorizationPolicy = authorizationPolicy;
    }

    /**
     * Validates bearer credentials before routing protected API requests.
     * The token is never logged, decoded into response headers, or rewritten.
     *
     * @param exchange current gateway exchange
     * @param chain remaining gateway filter chain
     * @return the downstream result or a generic 401 response
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (isPublicLogin(exchange)
                || HttpMethod.OPTIONS.equals(exchange.getRequest().getMethod())) {
            // Browser CORS preflight requests carry no bearer token. Forward them
            // so the backend's existing CORS policy can answer the preflight.
            return chain.filter(exchange);
        }

        String authorization = exchange.getRequest()
                .getHeaders()
                .getFirst(HttpHeaders.AUTHORIZATION);

        if (authorization == null
                || !authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return unauthorized(exchange);
        }

        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        final Claims claims;
        try {
            claims = tokenValidator.validate(token);
        } catch (JwtException | IllegalArgumentException exception) {
            // Never expose parser details or token contents to callers or logs.
            return unauthorized(exchange);
        }

        if (!authorizationPolicy.isAuthorized(exchange, claims)) {
            return forbidden(exchange);
        }

        // Forward the original Authorization header unchanged. The backend
        // independently validates the token and checks current account state/roles.
        return chain.filter(exchange);
    }

    /**
     * Places correlation-ID processing before authentication while keeping this
     * check ahead of route forwarding.
     *
     * @return gateway filter order
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    private boolean isPublicLogin(ServerWebExchange exchange) {
        return HttpMethod.POST.equals(exchange.getRequest().getMethod())
                && PUBLIC_LOGIN_PATH.equals(exchange.getRequest().getURI().getPath());
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        exchange.getResponse().getHeaders().setCacheControl("no-store");
        return exchange.getResponse().setComplete();
    }

    private Mono<Void> forbidden(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        exchange.getResponse().getHeaders().setCacheControl("no-store");
        return exchange.getResponse().setComplete();
    }
}
