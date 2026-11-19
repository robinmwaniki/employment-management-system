package com.ems.gateway.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests that Retry-After is present only for rate-limited responses. */
class RateLimitRetryAfterWebFilterTests {

    private final RateLimitRetryAfterWebFilter filter = new RateLimitRetryAfterWebFilter("7");

    @Test
    void addsRetryAfterForTooManyRequests() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api").build());
        filter.filter(exchange, current -> {
            current.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return current.getResponse().setComplete();
        }).block();
        assertThat(exchange.getResponse().getHeaders().getFirst("Retry-After")).isEqualTo("7");
    }

    @Test
    void rejectsInvalidRetryAfterConfiguration() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> new RateLimitRetryAfterWebFilter("0"))
                .isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> new RateLimitRetryAfterWebFilter("five"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void doesNotAddRetryAfterForSuccessfulResponse() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api").build());
        filter.filter(exchange, current -> {
            current.getResponse().setStatusCode(HttpStatus.OK);
            return current.getResponse().setComplete();
        }).block();
        assertThat(exchange.getResponse().getHeaders().getFirst("Retry-After")).isNull();
    }
}
