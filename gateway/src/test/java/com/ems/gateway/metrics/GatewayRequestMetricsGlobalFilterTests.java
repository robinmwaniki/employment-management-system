package com.ems.gateway.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies bounded-cardinality gateway counters and latency timers. */
class GatewayRequestMetricsGlobalFilterTests {

    @Test
    void recordsRouteMethodOutcomeAndDurationWithoutPathOrQueryTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        GatewayRequestMetricsGlobalFilter filter = new GatewayRequestMetricsGlobalFilter(registry);
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/employees/42?email=private@example.test").build());
        exchange.getAttributes().put(
                ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR,
                route("ems-employees-api"));

        filter.filter(exchange, ignored -> {
            exchange.getResponse().setStatusCode(HttpStatus.ACCEPTED);
            return exchange.getResponse().setComplete();
        }).block();

        assertThat(registry.get("ems.gateway.requests")
                .tags("routeId", "ems-employees-api", "method", "GET", "outcome", "success")
                .counter().count()).isEqualTo(1.0);
        assertThat(registry.get("ems.gateway.request.duration")
                .tags("routeId", "ems-employees-api", "method", "GET", "outcome", "success")
                .timer().count()).isEqualTo(1L);
        assertThat(registry.getMeters()).allSatisfy(meter ->
                assertThat(meter.getId().getTags().toString())
                        .doesNotContain("42", "private@example.test"));
    }

    @Test
    void recordsServerErrorWhenTheGatewayFilterChainFails() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        GatewayRequestMetricsGlobalFilter filter = new GatewayRequestMetricsGlobalFilter(registry);
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/reports/monthly").build());
        exchange.getAttributes().put(
                ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR,
                route("ems-reports-api"));

        filter.filter(exchange, ignored -> Mono.error(new IllegalStateException("sensitive detail")))
                .onErrorResume(IllegalStateException.class, ignored -> Mono.empty())
                .block();

        assertThat(registry.get("ems.gateway.requests")
                .tags("routeId", "ems-reports-api", "method", "GET", "outcome", "server_error")
                .counter().count()).isEqualTo(1.0);
    }

    @Test
    void classifiesUnknownMethodsIntoOneBoundedTag() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        GatewayRequestMetricsGlobalFilter filter = new GatewayRequestMetricsGlobalFilter(registry);
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.method(HttpMethod.TRACE, "/api/employees").build());
        exchange.getAttributes().put(
                ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR,
                route("ems-employees-api"));

        filter.filter(exchange, ignored -> {
            exchange.getResponse().setStatusCode(HttpStatus.NO_CONTENT);
            return exchange.getResponse().setComplete();
        }).block();

        assertThat(registry.get("ems.gateway.requests")
                .tags("routeId", "ems-employees-api", "method", "OTHER", "outcome", "success")
                .counter().count()).isEqualTo(1.0);
    }

    private Route route(String id) {
        return Route.async()
                .id(id)
                .uri(URI.create("http://localhost"))
                .predicate(ignored -> true)
                .build();
    }
}
