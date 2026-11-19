package com.ems.gateway.ratelimit;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
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
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the Redis token bucket against the disposable Redis service used by gateway CI.
 *
 * <p>The test is skipped during ordinary local builds unless the explicit CI flag is set.
 * It uses a loopback-only trusted proxy and a documentation-only client IP, never a
 * production address or credential.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "JWT_SECRET=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "EMS_CORS_ALLOWED_ORIGINS=http://localhost:5173",
        "EMS_RATE_LIMIT_ENABLED=true",
        "EMS_TRUSTED_PROXY_CIDRS=127.0.0.1/32",
        "EMS_RATE_LIMIT_LOGIN_PER_SECOND=1",
        "EMS_RATE_LIMIT_LOGIN_BURST=1",
        "EMS_RATE_LIMIT_LOGIN_TOKENS=1",
        "EMS_GATEWAY_MAX_REQUEST_SIZE=1KB"
})
@EnabledIfEnvironmentVariable(
        named = "EMS_GATEWAY_REDIS_INTEGRATION_TESTS",
        matches = "true")
class GatewayRedisRateLimitIntegrationTests {

    private static final String TEST_SECRET =
            "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private static final SecretKey SIGNING_KEY =
            Keys.hmacShaKeyFor(Decoders.BASE64.decode(TEST_SECRET));
    private static final String TEST_CLIENT_IP = "198.51.100.77";
    private static final AtomicInteger BACKEND_REQUESTS = new AtomicInteger();

    private static DisposableServer mockBackend;

    @LocalServerPort
    private int gatewayPort;

    @DynamicPropertySource
    static void backendProperties(DynamicPropertyRegistry registry) {
        if (mockBackend == null) {
            mockBackend = HttpServer.create()
                    .host("127.0.0.1")
                    .port(0)
                    .handle((request, response) -> request.receive()
                            .aggregate()
                            .asString()
                            .defaultIfEmpty("")
                            .flatMap(body -> {
                                BACKEND_REQUESTS.incrementAndGet();
                                return response.status(202).sendString(Mono.just("accepted")).then();
                            }))
                    .bindNow();
        }
        registry.add("ems.backend.uri", () -> "http://127.0.0.1:" + mockBackend.port());
    }

    @AfterAll
    static void stopMockBackend() {
        if (mockBackend != null) {
            mockBackend.disposeNow();
            mockBackend = null;
        }
    }

    @Test
    void enforcesSharedRedisTokenBucketAndDoesNotForwardRejectedRequest() {
        WebTestClient client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + gatewayPort)
                .build();

        client.post()
                .uri("/api/auth/login")
                .header("X-Forwarded-For", TEST_CLIENT_IP)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("{\"username\":\"ci-user\"}")
                .exchange()
                .expectStatus().isAccepted();

        client.post()
                .uri("/api/auth/login")
                .header("X-Forwarded-For", TEST_CLIENT_IP)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("{\"username\":\"ci-user\"}")
                .exchange()
                .expectStatus().isEqualTo(429)
                .expectHeader().valueEquals("Retry-After", "5")
                .expectBody()
                .jsonPath("$.code").isEqualTo("GATEWAY_RATE_LIMITED");

        assertThat(BACKEND_REQUESTS).hasValue(1);
    }
}
