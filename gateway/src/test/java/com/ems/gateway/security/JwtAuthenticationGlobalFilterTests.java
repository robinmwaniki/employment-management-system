package com.ems.gateway.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.List;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies gateway bearer enforcement and the single public login route.
 */
class JwtAuthenticationGlobalFilterTests {

    private static final String TEST_SECRET =
            "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private static final String ISSUER = "employee-management-system";

    private final JwtAuthenticationGlobalFilter filter =
            new JwtAuthenticationGlobalFilter(
                    new JwtTokenValidator(TEST_SECRET), new JwtRouteAuthorizationPolicy());
    private final SecretKey signingKey =
            Keys.hmacShaKeyFor(Decoders.BASE64.decode(TEST_SECRET));

    @Test
    void permitsUnauthenticatedPostToPublicLoginEndpoint() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/auth/login").build());
        AtomicBoolean forwarded = new AtomicBoolean();

        filter.filter(exchange, forwardedExchange -> {
            forwarded.set(true);
            return Mono.empty();
        }).block();

        assertThat(forwarded).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }


    @Test
    void forwardsCorsPreflightWithoutBearerToken() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.options("/api/employees")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Authorization,Content-Type")
                        .build());
        AtomicBoolean forwarded = new AtomicBoolean();

        filter.filter(exchange, forwardedExchange -> {
            forwarded.set(true);
            return Mono.empty();
        }).block();

        assertThat(forwarded).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void rejectsMissingBearerTokenForProtectedApiRoute() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/employees").build());
        AtomicBoolean forwarded = new AtomicBoolean();

        filter.filter(exchange, forwardedExchange -> {
            forwarded.set(true);
            return Mono.empty();
        }).block();

        assertThat(forwarded).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE))
                .isEqualTo("Bearer");
    }

    @Test
    void rejectsMalformedAndNonBearerAuthorizationHeaders() {
        for (String authorization : new String[]{"Bearer not-a-jwt", "Basic dXNlcjpwYXNz", "Bearer "}) {
            ServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/employees")
                            .header(HttpHeaders.AUTHORIZATION, authorization)
                            .build());

            filter.filter(exchange, ignored -> Mono.empty()).block();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Test
    void rejectsExpiredBearerToken() {
        String expired = token(Instant.now().minusSeconds(30));
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/employees")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + expired)
                        .build());

        filter.filter(exchange, ignored -> Mono.empty()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void forwardsValidTokenUnchangedForBackendRevalidation() {
        String valid = token(Instant.now().plusSeconds(300));
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/employees")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + valid)
                        .build());
        AtomicBoolean forwarded = new AtomicBoolean();

        filter.filter(exchange, forwardedExchange -> {
            forwarded.set(true);
            assertThat(forwardedExchange.getRequest().getHeaders()
                    .getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer " + valid);
            return Mono.empty();
        }).block();

        assertThat(forwarded).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }


    @Test
    void allowsEmployeeRoleOnDepartmentsButNotStaffOnlyRoutes() {
        String employeeToken = tokenWithRoles(Instant.now().plusSeconds(300), "ROLE_EMPLOYEE");
        ServerWebExchange departments = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/departments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + employeeToken)
                        .build());
        AtomicBoolean departmentsForwarded = new AtomicBoolean();

        filter.filter(departments, ignored -> {
            departmentsForwarded.set(true);
            return Mono.empty();
        }).block();

        assertThat(departmentsForwarded).isTrue();

        ServerWebExchange employees = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/employees")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + employeeToken)
                        .build());
        AtomicBoolean employeesForwarded = new AtomicBoolean();

        filter.filter(employees, ignored -> {
            employeesForwarded.set(true);
            return Mono.empty();
        }).block();

        assertThat(employeesForwarded).isFalse();
        assertThat(employees.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void allowsHrManagerOnStaffRoutes() {
        String hrToken = tokenWithRoles(Instant.now().plusSeconds(300), "ROLE_HR_MANAGER");
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/employees")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + hrToken)
                        .build());
        AtomicBoolean forwarded = new AtomicBoolean();

        filter.filter(exchange, ignored -> {
            forwarded.set(true);
            return Mono.empty();
        }).block();

        assertThat(forwarded).isTrue();
    }

    @Test
    void registrationRequiresAdministratorRole() {
        String hrToken = tokenWithRoles(Instant.now().plusSeconds(300), "ROLE_HR_MANAGER");
        ServerWebExchange hrRequest = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/auth/register")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + hrToken)
                        .build());

        filter.filter(hrRequest, ignored -> Mono.empty()).block();

        assertThat(hrRequest.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        String adminToken = tokenWithRoles(Instant.now().plusSeconds(300), "ROLE_ADMIN");
        ServerWebExchange adminRequest = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/auth/register")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .build());
        AtomicBoolean forwarded = new AtomicBoolean();

        filter.filter(adminRequest, ignored -> {
            forwarded.set(true);
            return Mono.empty();
        }).block();

        assertThat(forwarded).isTrue();
    }

    @Test
    void rejectsLegacyTokenWithoutSignedRoleClaims() {
        String legacyToken = tokenWithRoles(Instant.now().plusSeconds(300));
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/employees")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + legacyToken)
                        .build());

        filter.filter(exchange, ignored -> Mono.empty()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private String token(Instant expiresAt) {
        return tokenWithRoles(expiresAt, "ROLE_ADMIN");
    }

    private String tokenWithRoles(Instant expiresAt, String... roles) {
        return Jwts.builder()
                .issuer(ISSUER)
                .subject("admin@example.test")
                .issuedAt(new Date())
                .expiration(Date.from(expiresAt))
                .claim("roles", List.of(roles))
                .signWith(signingKey)
                .compact();
    }
}
