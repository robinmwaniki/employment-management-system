package com.ems.gateway;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.beans.factory.annotation.Autowired;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.http.HttpHeaders;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.SecretKey;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies gateway startup and HTTP forwarding against an isolated local backend.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "JWT_SECRET=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "EMS_CORS_ALLOWED_ORIGINS=http://localhost:5173",
        "EMS_RATE_LIMIT_ENABLED=false",
        "EMS_MANAGEMENT_API_KEY=test-management-key",
        "EMS_GATEWAY_MAX_REQUEST_SIZE=1KB",
        "management.tracing.sampling.probability=1.0",
        "management.tracing.export.otlp.enabled=false"
})
class ApiGatewayApplicationTests {

    private static final String TEST_SECRET =
            "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private static final SecretKey SIGNING_KEY =
            Keys.hmacShaKeyFor(Decoders.BASE64.decode(TEST_SECRET));
    private static DisposableServer mockBackend;
    private static final AtomicInteger BACKEND_REQUEST_COUNT = new AtomicInteger();
    private static final ConcurrentHashMap<String, AtomicInteger> RETRY_REQUESTS = new ConcurrentHashMap<>();
    private static final AtomicReference<String> BACKEND_AUTHORIZATION = new AtomicReference<>();
    private static final AtomicReference<String> BACKEND_TRACEPARENT = new AtomicReference<>();

    @LocalServerPort
    private int gatewayPort;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    private RouteLocator routeLocator;

    /**
     * Supplies an ephemeral backend address before the gateway context is created.
     *
     * @param registry dynamic test properties
     */
    @DynamicPropertySource
    static void backendProperties(DynamicPropertyRegistry registry) {
        if (mockBackend == null) {
            mockBackend = HttpServer.create()
                    .host("127.0.0.1")
                    .port(0)
                    .handle((request, response) -> {
                        BACKEND_REQUEST_COUNT.incrementAndGet();
                        String requestPath = request.uri();
                        String retryKey = request.method().name() + ":" + requestPath;
                        boolean retryScenario = requestPath.contains("/retry-once-");
                        int retryAttempt = retryScenario
                                ? RETRY_REQUESTS.computeIfAbsent(retryKey, ignored -> new AtomicInteger())
                                        .incrementAndGet()
                                : 0;
                        int responseStatus = retryScenario && retryAttempt == 1 ? 503 : 202;
                        if (requestPath.endsWith("/backend-auth-check")) {
                            BACKEND_AUTHORIZATION.set(request.requestHeaders().get("Authorization"));
                            responseStatus = BACKEND_AUTHORIZATION.get() != null
                                    && BACKEND_AUTHORIZATION.get().startsWith("Bearer ") ? 202 : 401;
                        }
                        if (requestPath.endsWith("/trace-check")) {
                            BACKEND_TRACEPARENT.set(request.requestHeaders().get("traceparent"));
                        }
                        final int finalResponseStatus = responseStatus;
                        String origin = request.requestHeaders().get("Origin");
                        if (origin != null) {
                            // Simulate the legacy backend CORS response so the
                            // gateway's dedupe filter can be verified end-to-end.
                            response.header("Access-Control-Allow-Origin", origin);
                            response.header("Access-Control-Expose-Headers", "Authorization");
                        }
                        final int backendResponseStatus = responseStatus;
                        return request.receive()
                                .aggregate()
                                .asString()
                                .defaultIfEmpty("")
                                .flatMap(body -> {
                                    if ("/api/employees/backend-forbidden".equals(requestPath)) {
                                        return response.status(403)
                                                .sendString(Mono.just(
                                                        "{\"code\":\"BACKEND_RESOURCE_FORBIDDEN\"}"))
                                                .then();
                                    }
                                    return response
                                            .status(finalResponseStatus)
                                            .sendString(Mono.just(
                                                    request.method().name() + " "
                                                            + request.uri() + "|" + body))
                                            .then();
                                });
                    })
                    .bindNow();
        }
        registry.add("ems.backend.uri",
                () -> "http://127.0.0.1:" + mockBackend.port());
    }


    @BeforeEach
    void resetBackendRequestCount() {
        BACKEND_REQUEST_COUNT.set(0);
        RETRY_REQUESTS.clear();
        BACKEND_AUTHORIZATION.set(null);
        BACKEND_TRACEPARENT.set(null);
    }

    /**
     * Releases the local backend after the forwarding tests complete.
     */
    @AfterAll
    static void stopMockBackend() {
        if (mockBackend != null) {
            mockBackend.disposeNow();
            mockBackend = null;
        }
    }

    /**
     * Creates an HTTP client bound to the embedded gateway server.
     *
     * @return client for the random test port
     */
    private WebTestClient client() {
        return WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + gatewayPort)
                .build();
    }

    /**
     * Confirms that the gateway starts on its configured reactive port.
     */
    @Test
    void configuresTimeoutRetryAndCircuitBreakerForEveryApiRoute() {
        var routes = routeLocator.getRoutes().collectList().block();

        assertThat(routes).hasSize(15);
        assertThat(routes).allSatisfy(route -> {
            assertThat(route.getMetadata()).containsKey("response-timeout");
            assertThat(route.getFilters()).hasSizeGreaterThanOrEqualTo(3);
        });
    }


    @Test
    void retriesTransientGetFailuresWithinConfiguredLimit() {
        String path = "/api/employees/retry-once-get";

        client().get()
                .uri(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken())
                .exchange()
                .expectStatus().isAccepted();

        assertThat(RETRY_REQUESTS.get("GET:" + path)).hasValue(2);
    }

    @Test
    void neverRetriesPostRequests() {
        String path = "/api/employees/retry-once-post";

        client().post()
                .uri(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken())
                .bodyValue("{\"name\":\"test employee\"}")
                .exchange()
                .expectStatus().isEqualTo(503);

        assertThat(RETRY_REQUESTS.get("POST:" + path)).hasValue(1);
    }

    @Test
    void exposesOnlyExplicitReadOnlyActuatorEndpoints() {
        client().get().uri("/actuator/health").exchange()
                .expectStatus().value(status -> assertThat(status).isIn(200, 503));
        client().get().uri("/actuator/info").exchange().expectStatus().isOk();
        client().get().uri("/actuator/metrics").exchange().expectStatus().isUnauthorized();
        client().get().uri("/actuator/metrics")
                .header("X-EMS-Management-Key", "test-management-key")
                .exchange().expectStatus().isOk();

        client().get().uri("/actuator/env").exchange().expectStatus().isNotFound();
        client().get().uri("/actuator/beans").exchange().expectStatus().isNotFound();
        client().get().uri("/actuator/gateway/routes").exchange().expectStatus().isNotFound();
    }

    @Test
    void exposesLivenessAndReadinessProbesIndependentOfBackendCircuitState() {
        CircuitBreaker employeesBreaker =
                circuitBreakerRegistry.circuitBreaker("emsEmployeesCircuitBreaker");
        employeesBreaker.transitionToOpenState();

        try {
            client().get().uri("/actuator/health/liveness")
                    .exchange().expectStatus().isOk()
                    .expectBody().jsonPath("$.status").isEqualTo("UP");
            client().get().uri("/actuator/health/readiness")
                    .exchange().expectStatus().isOk()
                    .expectBody().jsonPath("$.status").isEqualTo("UP");
            client().get().uri("/livez")
                    .exchange().expectStatus().isOk()
                    .expectBody().jsonPath("$.status").isEqualTo("UP");
            client().get().uri("/readyz")
                    .exchange().expectStatus().isOk()
                    .expectBody().jsonPath("$.status").isEqualTo("UP");
        } finally {
            employeesBreaker.reset();
        }
    }

    @Test
    void recordsGatewayRequestCountLatencyAndBoundedOutcomeTags() {
        client().get()
                .uri("/api/employees/metrics-check")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken())
                .exchange()
                .expectStatus().isAccepted();

        String metric = client().get()
                .uri("/actuator/metrics/spring.cloud.gateway.requests")
                .header("X-EMS-Management-Key", "test-management-key")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        assertThat(metric).contains(
                "spring.cloud.gateway.requests",
                "COUNT",
                "TOTAL_TIME",
                "routeId",
                "httpMethod",
                "outcome",
                "status");
        assertThat(metric).doesNotContain("\"tag\":\"path\"");
    }


    @Test
    void exposesSafeHealthProbesAndProtectsMetricsEndpoint() {
        client().get()
                .uri("/actuator/health/liveness")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP")
                .jsonPath("$.components").doesNotExist();

        client().get()
                .uri("/actuator/health/readiness")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP")
                .jsonPath("$.components").doesNotExist();

        client().get()
                .uri("/actuator/env")
                .exchange()
                .expectStatus().isNotFound();

        client().get()
                .uri("/api/employees/metrics-check")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken())
                .exchange()
                .expectStatus().isAccepted();

        client().get()
                .uri("/actuator/metrics/ems.gateway.requests")
                .exchange()
                .expectStatus().isUnauthorized();

        client().get()
                .uri("/actuator/metrics/ems.gateway.requests")
                .header("X-EMS-Management-Key", "test-management-key")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.name").isEqualTo("ems.gateway.requests");
    }




    @Test
    void propagatesW3cTraceContextAndCreatesAChildSpanForBackendRequest() {
        String incomingTraceparent =
                "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

        client().get()
                .uri("/api/employees/trace-check")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken())
                .header("traceparent", incomingTraceparent)
                .exchange()
                .expectStatus().isAccepted();

        String downstreamTraceparent = BACKEND_TRACEPARENT.get();
        assertThat(downstreamTraceparent).isNotNull();
        assertThat(downstreamTraceparent)
                .matches("^00-4bf92f3577b34da6a3ce929d0e0e4736-[0-9a-f]{16}-01$");
        assertThat(downstreamTraceparent.substring(36, 52))
                .isNotEqualTo(incomingTraceparent.substring(36, 52));
    }

    @Test
    void createsW3cTraceContextWhenTheIncomingRequestHasNoTraceparent() {
        client().get()
                .uri("/api/employees/trace-check")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken())
                .exchange()
                .expectStatus().isAccepted();

        assertThat(BACKEND_TRACEPARENT.get())
                .matches("^00-[0-9a-f]{32}-[0-9a-f]{16}-01$");
    }

    @Test
    void forwardsTheOriginalBearerTokenForBackendRevalidation() {
        String token = validToken();

        client().get()
                .uri("/api/employees/backend-auth-check")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isAccepted();

        assertThat(BACKEND_AUTHORIZATION.get()).isEqualTo("Bearer " + token);
        assertThat(BACKEND_REQUEST_COUNT).hasValue(1);
    }

    @Test
    void rejectsInvalidJwtBeforeBackendForwarding() {
        client().get()
                .uri("/api/employees/invalid-jwt")
                .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-valid-jwt")
                .exchange()
                .expectStatus().isUnauthorized();

        assertThat(BACKEND_REQUEST_COUNT).hasValue(0);
    }

    @Test
    void rejectsEmployeeRoleBypassAttemptBeforeBackendForwarding() {
        String employeeToken = tokenWithRoles("ROLE_EMPLOYEE");

        client().get()
                .uri("/api/employees/role-bypass")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + employeeToken)
                .exchange()
                .expectStatus().isForbidden();

        assertThat(BACKEND_REQUEST_COUNT).hasValue(0);
    }

    @Test
    void rejectsOversizedRequestBeforeForwardingToBackend() {
        String oversizedBody = "x".repeat(1025);

        client().post()
                .uri("/api/employees/oversized")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken())
                .header(HttpHeaders.CONTENT_TYPE, "application/octet-stream")
                .header(HttpHeaders.CONTENT_LENGTH,
                        Integer.toString(oversizedBody.getBytes(java.nio.charset.StandardCharsets.UTF_8).length))
                .bodyValue(oversizedBody)
                .exchange()
                .expectStatus().isEqualTo(413);

        assertThat(BACKEND_REQUEST_COUNT).hasValue(0);
    }

    @Test
    void preservesBackendResourceAuthorizationDenial() {
        client().get()
                .uri("/api/employees/backend-forbidden")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validManagerToken())
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("BACKEND_RESOURCE_FORBIDDEN");

        // The gateway permits the manager role and preserves the backend's
        // independent resource-level authorization decision.
        assertThat(BACKEND_REQUEST_COUNT).hasValue(1);
    }

    @Test
    void contextLoads() {
        assertThat(gatewayPort).isPositive();
    }

    /**
     * Confirms that path and query parameters reach the configured backend.
     */
    @Test
    void forwardsGetRequestAndQueryString() {
        client().get()
                .uri("/api/employees/42?include=manager")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken())
                .exchange()
                .expectStatus().isAccepted()
                .expectBody(String.class)
                .isEqualTo("GET /api/employees/42?include=manager|");
    }

    /**
     * Confirms that the gateway preserves the HTTP method and request body.
     */
    @Test
    void forwardsPostRequestAndBody() {
        client().post()
                .uri("/api/auth/login")
                .header("Content-Type", "application/json")
                .bodyValue("{\"username\":\"test-user\"}")
                .exchange()
                .expectStatus().isAccepted()
                .expectBody(String.class)
                .isEqualTo("POST /api/auth/login|{\"username\":\"test-user\"}");
    }

    /**
     * Confirms that paths outside the declared API surface are not proxied.
     */
    @Test
    void doesNotForwardUnmatchedPath() {
        client().get()
                .uri("/not-an-api-route")
                .exchange()
                .expectStatus().isNotFound();
    }


    @Test
    void handlesAllowedCorsPreflightAtGatewayWithoutCallingBackend() {
        client().options()
                .uri("/api/employees")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                        "Authorization,X-Correlation-ID")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        "http://localhost:5173")
                .expectHeader().valueEquals("X-Content-Type-Options", "nosniff");

        assertThat(BACKEND_REQUEST_COUNT).hasValue(0);
    }

    @Test
    void rejectsDisallowedCorsOriginBeforeBackendForwarding() {
        client().options()
                .uri("/api/employees")
                .header(HttpHeaders.ORIGIN, "https://attacker.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization")
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)
                .expectHeader().valueEquals("X-Frame-Options", "DENY");

        assertThat(BACKEND_REQUEST_COUNT).hasValue(0);
    }

    @Test
    void deduplicatesBackendCorsHeadersAndExposesCorrelationId() {
        EntityExchangeResult<String> result = client().get()
                .uri("/api/employees/42")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken())
                .exchange()
                .expectStatus().isAccepted()
                .expectBody(String.class)
                .returnResult();

        assertThat(result.getResponseHeaders().get(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                .containsExactly("http://localhost:5173");
        assertThat(result.getResponseHeaders().getAccessControlExposeHeaders())
                .contains("Authorization", "X-Correlation-ID");
        assertThat(result.getResponseHeaders()
                .getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)).isNull();
        assertThat(result.getResponseHeaders().getFirst("X-Content-Type-Options"))
                .isEqualTo("nosniff");
        assertThat(BACKEND_REQUEST_COUNT).hasValue(1);
    }


    @Test
    void addsSecurityHeadersToUnauthorizedGatewayResponses() {
        client().get()
                .uri("/api/employees/42")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals("X-Content-Type-Options", "nosniff")
                .expectHeader().valueEquals("X-Frame-Options", "DENY")
                .expectHeader().valueEquals("Referrer-Policy", "strict-origin-when-cross-origin")
                .expectHeader().exists("Strict-Transport-Security");

        assertThat(BACKEND_REQUEST_COUNT).hasValue(0);
    }


    private String tokenWithRoles(String... roles) {
        return Jwts.builder()
                .issuer("employee-management-system")
                .subject("employee@example.test")
                .claim("roles", List.of(roles))
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .signWith(SIGNING_KEY)
                .compact();
    }

    private String validManagerToken() {
        return Jwts.builder()
                .issuer("employee-management-system")
                .subject("manager@example.test")
                .claim("roles", List.of("ROLE_HR_MANAGER"))
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .signWith(SIGNING_KEY)
                .compact();
    }

    private String validToken() {
        return Jwts.builder()
                .issuer("employee-management-system")
                .subject("admin@example.test")
                .claim("roles", List.of("ROLE_ADMIN"))
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .signWith(SIGNING_KEY)
                .compact();
    }

}
