package com.ems.gateway.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises retries, route timeouts, circuit-breaker states and safe fallbacks
 * through a real gateway instance and an isolated in-process backend.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "JWT_SECRET=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "EMS_CORS_ALLOWED_ORIGINS=http://localhost:5173",
        "EMS_RATE_LIMIT_ENABLED=false",
        "EMS_GATEWAY_EMPLOYEES_RESPONSE_TIMEOUT_MS=150",
        "EMS_GATEWAY_SAFE_RETRIES=2",
        "EMS_GATEWAY_CB_MIN_CALLS=100",
        "EMS_GATEWAY_CB_TIMEOUT=15s"
})
class GatewayResilienceIntegrationTests {

    private static final String TEST_SECRET =
            "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private static final SecretKey SIGNING_KEY =
            Keys.hmacShaKeyFor(Decoders.BASE64.decode(TEST_SECRET));

    private static final AtomicInteger BACKEND_REQUESTS = new AtomicInteger();
    private static final AtomicInteger RETRY_ONCE_REQUESTS = new AtomicInteger();
    private static final AtomicInteger RETRY_EXHAUSTED_REQUESTS = new AtomicInteger();
    private static final AtomicInteger POST_FAILURE_REQUESTS = new AtomicInteger();
    private static final AtomicInteger SLOW_REQUESTS = new AtomicInteger();

    private static DisposableServer mockBackend;

    @LocalServerPort
    private int gatewayPort;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @DynamicPropertySource
    static void backendProperties(DynamicPropertyRegistry registry) {
        if (mockBackend == null) {
            mockBackend = HttpServer.create()
                    .host("127.0.0.1")
                    .port(0)
                    .handle((request, response) -> {
                        BACKEND_REQUESTS.incrementAndGet();
                        String path = request.uri().split("\\?", 2)[0];
                        return request.receive()
                                .aggregate()
                                .asString()
                                .defaultIfEmpty("")
                                .flatMap(ignored -> respond(path, response));
                    })
                    .bindNow();
        }
        registry.add("ems.backend.uri", () -> "http://127.0.0.1:" + mockBackend.port());
    }

    private static Mono<Void> respond(
            String path,
            reactor.netty.http.server.HttpServerResponse response) {
        if ("/api/employees/retry-once".equals(path)) {
            int attempt = RETRY_ONCE_REQUESTS.incrementAndGet();
            if (attempt == 1) {
                return response.status(503).sendString(Mono.just("temporary")).then();
            }
        }
        if ("/api/employees/retry-exhausted".equals(path)) {
            RETRY_EXHAUSTED_REQUESTS.incrementAndGet();
            return response.status(503).sendString(Mono.just("temporary")).then();
        }
        if ("/api/employees/post-failure".equals(path)) {
            POST_FAILURE_REQUESTS.incrementAndGet();
            return response.status(503).sendString(Mono.just("temporary")).then();
        }
        if ("/api/employees/slow".equals(path)) {
            SLOW_REQUESTS.incrementAndGet();
            return Mono.delay(Duration.ofMillis(700))
                    .then(response.status(202).sendString(Mono.just("late response")).then());
        }
        return response.status(202).sendString(Mono.just("ok")).then();
    }

    @BeforeEach
    void resetState() {
        BACKEND_REQUESTS.set(0);
        RETRY_ONCE_REQUESTS.set(0);
        RETRY_EXHAUSTED_REQUESTS.set(0);
        POST_FAILURE_REQUESTS.set(0);
        SLOW_REQUESTS.set(0);
        circuitBreakerRegistry.circuitBreaker("emsEmployeesCircuitBreaker").reset();
        circuitBreakerRegistry.circuitBreaker("emsCompanyProfileCircuitBreaker").reset();
    }

    @AfterAll
    static void stopMockBackend() {
        if (mockBackend != null) {
            mockBackend.disposeNow();
            mockBackend = null;
        }
    }

    @Test
    void forwardsCompanyProfileApiThroughDedicatedRoute() {
        client().get()
                .uri("/api/company-profile")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken())
                .exchange()
                .expectStatus().isAccepted()
                .expectBody().isEqualTo("ok");

        assertThat(BACKEND_REQUESTS).hasValue(1);
    }

    @Test
    void retriesTransientGetFailureAndThenSucceeds() {
        client().get()
                .uri("/api/employees/retry-once")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken())
                .exchange()
                .expectStatus().isAccepted();

        assertThat(RETRY_ONCE_REQUESTS).hasValue(2);
        assertThat(BACKEND_REQUESTS).hasValue(2);
    }

    @Test
    void boundsGetRetriesToConfiguredMaximum() {
        client().get()
                .uri("/api/employees/retry-exhausted")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken())
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.code").isEqualTo("UPSTREAM_UNAVAILABLE");

        assertThat(RETRY_EXHAUSTED_REQUESTS).hasValue(3);
        assertThat(BACKEND_REQUESTS).hasValue(3);
    }

    @Test
    void neverRetriesPostRequests() {
        client().post()
                .uri("/api/employees/post-failure")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken())
                .bodyValue("{}")
                .exchange()
                .expectStatus().isEqualTo(503);

        assertThat(POST_FAILURE_REQUESTS).hasValue(1);
        assertThat(BACKEND_REQUESTS).hasValue(1);
    }

    @Test
    void mapsRouteResponseTimeoutToSafeGatewayTimeoutFallback() {
        client().post()
                .uri("/api/employees/slow")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken())
                .bodyValue("{}")
                .exchange()
                .expectStatus().isEqualTo(504)
                .expectBody()
                .jsonPath("$.code").isEqualTo("UPSTREAM_TIMEOUT")
                .jsonPath("$.message").isEqualTo("The upstream service did not respond in time.");

        assertThat(SLOW_REQUESTS).hasValue(1);
        assertThat(BACKEND_REQUESTS).hasValue(1);
    }

    @Test
    void openCircuitShortCircuitsBackendAndClosedCircuitForwards() {
        CircuitBreaker breaker =
                circuitBreakerRegistry.circuitBreaker("emsEmployeesCircuitBreaker");

        breaker.transitionToOpenState();
        client().get()
                .uri("/api/employees/circuit-open")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken())
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.code").isEqualTo("UPSTREAM_UNAVAILABLE");

        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(BACKEND_REQUESTS).hasValue(0);

        breaker.reset();
        client().get()
                .uri("/api/employees/circuit-closed")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken())
                .exchange()
                .expectStatus().isAccepted();

        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(BACKEND_REQUESTS).hasValue(1);
    }

    private WebTestClient client() {
        return WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + gatewayPort)
                .build();
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
