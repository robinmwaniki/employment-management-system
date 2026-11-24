package com.ems.gateway.security;

import com.ems.gateway.error.GatewayErrorResponseWriter;
import tools.jackson.databind.json.JsonMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
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

    private final GatewayErrorResponseWriter errorResponseWriter =
            new GatewayErrorResponseWriter(JsonMapper.builder().build());
    private final JwtAuthenticationGlobalFilter filter =
            new JwtAuthenticationGlobalFilter(
                    new JwtTokenValidator(TEST_SECRET),
                    new JwtRouteAuthorizationPolicy(),
                    errorResponseWriter);
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
    void permitsUnauthenticatedPostToPublicDemoLoginEndpoint() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/demo/login").build());
        AtomicBoolean forwarded = new AtomicBoolean();

        filter.filter(exchange, forwardedExchange -> {
            forwarded.set(true);
            return Mono.empty();
        }).block();

        assertThat(forwarded).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void protectsEveryOtherDemoApiRequest() {
        for (ServerWebExchange exchange : List.of(
                MockServerWebExchange.from(MockServerHttpRequest.get("/api/demo/login").build()),
                MockServerWebExchange.from(MockServerHttpRequest.post("/api/demo/other").build()))) {
            AtomicBoolean forwarded = new AtomicBoolean();

            filter.filter(exchange, ignored -> {
                forwarded.set(true);
                return Mono.empty();
            }).block();

            assertThat(forwarded).isFalse();
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
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
        MockServerWebExchange exchange = MockServerWebExchange.from(
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
        assertThat(((MockServerHttpResponse) exchange.getResponse()).getBodyAsString().block())
                .contains("\"GATEWAY_UNAUTHORIZED\"")
                .contains("\"status\":401");
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

        MockServerWebExchange employees = MockServerWebExchange.from(
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
        assertThat(((MockServerHttpResponse) employees.getResponse()).getBodyAsString().block())
                .contains("\"GATEWAY_FORBIDDEN\"")
                .contains("\"status\":403");
    }

    @Test
    void allowsEmployeeRoleOnAuthenticatedSelfServiceRoutesButNotStaffRoutes() {
        String employeeToken = tokenWithRoles(Instant.now().plusSeconds(300), "ROLE_EMPLOYEE");
        for (ServerWebExchange exchange : List.of(
                MockServerWebExchange.from(MockServerHttpRequest.get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + employeeToken).build()),
                MockServerWebExchange.from(MockServerHttpRequest.post("/api/auth/change-password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + employeeToken).build()),
                MockServerWebExchange.from(MockServerHttpRequest.get("/api/me/announcements")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + employeeToken).build()),
                MockServerWebExchange.from(MockServerHttpRequest.get("/api/me/performance?page=0&size=10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + employeeToken).build()))) {
            AtomicBoolean forwarded = new AtomicBoolean();

            filter.filter(exchange, ignored -> {
                forwarded.set(true);
                return Mono.empty();
            }).block();

            assertThat(forwarded).isTrue();
            assertThat(exchange.getResponse().getStatusCode()).isNull();
        }

        MockServerWebExchange payroll = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/payroll")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + employeeToken)
                        .build());
        AtomicBoolean payrollForwarded = new AtomicBoolean();

        filter.filter(payroll, ignored -> {
            payrollForwarded.set(true);
            return Mono.empty();
        }).block();

        assertThat(payrollForwarded).isFalse();
        assertThat(payroll.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void openApiDocumentRequiresAdministratorRole() {
        String hrToken = tokenWithRoles(Instant.now().plusSeconds(300), "ROLE_HR_MANAGER");
        MockServerWebExchange hrRequest = MockServerWebExchange.from(
                MockServerHttpRequest.get("/v3/api-docs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + hrToken)
                        .build());

        filter.filter(hrRequest, ignored -> Mono.empty()).block();

        assertThat(hrRequest.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        String adminToken = tokenWithRoles(Instant.now().plusSeconds(300), "ROLE_ADMIN");
        ServerWebExchange adminRequest = MockServerWebExchange.from(
                MockServerHttpRequest.get("/v3/api-docs")
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
        MockServerWebExchange hrRequest = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/auth/register")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + hrToken)
                        .build());

        filter.filter(hrRequest, ignored -> Mono.empty()).block();

        assertThat(hrRequest.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(((MockServerHttpResponse) hrRequest.getResponse()).getBodyAsString().block())
                .contains("\"GATEWAY_FORBIDDEN\"");

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
