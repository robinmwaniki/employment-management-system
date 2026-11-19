package com.ems.gateway;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies gateway startup and HTTP forwarding against an isolated local backend.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiGatewayApplicationTests {

    private static DisposableServer mockBackend;

    @LocalServerPort
    private int gatewayPort;

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
                    .handle((request, response) -> request.receive()
                            .aggregate()
                            .asString()
                            .defaultIfEmpty("")
                            .flatMap(body -> response
                                    .status(202)
                                    .sendString(Mono.just(
                                            request.method().name() + " "
                                                    + request.uri() + "|" + body))
                                    .then()))
                    .bindNow();
        }
        registry.add("ems.backend.uri",
                () -> "http://127.0.0.1:" + mockBackend.port());
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
}
