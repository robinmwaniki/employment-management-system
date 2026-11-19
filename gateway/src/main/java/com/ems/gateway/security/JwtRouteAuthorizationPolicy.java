package com.ems.gateway.security;

import io.jsonwebtoken.Claims;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Mirrors the current EMS backend's API role rules using authorities embedded
 * in a signed token. The backend remains authoritative and rechecks the user's
 * current role from the database on every request.
 */
@Component
public class JwtRouteAuthorizationPolicy {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final String ROLE_HR_MANAGER = "ROLE_HR_MANAGER";
    private static final String ROLE_EMPLOYEE = "ROLE_EMPLOYEE";

    private static final Set<String> KNOWN_ROLES =
            Set.of(ROLE_ADMIN, ROLE_HR_MANAGER, ROLE_EMPLOYEE);

    /**
     * Applies the current backend API policy to a verified JWT.
     *
     * <ul>
     *   <li>POST /api/auth/login is public.</li>
     *   <li>OPTIONS preflight requests are forwarded to backend CORS handling.</li>
     *   <li>/api/auth/register requires ROLE_ADMIN.</li>
     *   <li>/api/departments/** requires any known authenticated EMS role.</li>
     *   <li>All other routed API paths require ROLE_ADMIN or ROLE_HR_MANAGER.</li>
     *   <li>The OpenAPI JSON document requires ROLE_ADMIN.</li>
     * </ul>
     *
     * @param exchange current request
     * @param claims verified signed JWT claims
     * @return true when the token's signed role claims satisfy the route policy
     */
    public boolean isAuthorized(ServerWebExchange exchange, Claims claims) {
        if (isPublicRequest(exchange)) {
            return true;
        }

        Set<String> roles = extractRoles(claims);
        String path = exchange.getRequest().getURI().getPath();

        if (isOpenApiDocumentationPath(path)) {
            return roles.contains(ROLE_ADMIN);
        }

        if ("/api/auth/register".equals(path)) {
            return roles.contains(ROLE_ADMIN);
        }

        if ("/api/departments".equals(path) || path.startsWith("/api/departments/")) {
            return roles.stream().anyMatch(KNOWN_ROLES::contains);
        }

        return roles.contains(ROLE_ADMIN) || roles.contains(ROLE_HR_MANAGER);
    }

    private boolean isOpenApiDocumentationPath(String path) {
        return "/v3/api-docs".equals(path) || path.startsWith("/v3/api-docs/");
    }

    private boolean isPublicRequest(ServerWebExchange exchange) {
        return HttpMethod.OPTIONS.equals(exchange.getRequest().getMethod())
                || (HttpMethod.POST.equals(exchange.getRequest().getMethod())
                && "/api/auth/login".equals(exchange.getRequest().getURI().getPath()));
    }

    private Set<String> extractRoles(Claims claims) {
        Object roleClaim = claims.get("roles");
        if (!(roleClaim instanceof Collection<?> roleValues)) {
            return Set.of();
        }

        return roleValues.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(KNOWN_ROLES::contains)
                .collect(Collectors.toUnmodifiableSet());
    }
}
