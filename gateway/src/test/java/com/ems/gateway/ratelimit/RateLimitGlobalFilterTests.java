package com.ems.gateway.ratelimit;

import com.ems.gateway.error.GatewayErrorResponseWriter;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Verifies Redis token-bucket decisions, policy selection and fail-closed behavior.
 */
class RateLimitGlobalFilterTests {

    @Test
    void forwardsRequestWhenRedisAllowsIt() throws Exception {
        ReactiveStringRedisTemplate redis = redisReturning(List.of(1L, 4L));
        GatewayErrorResponseWriter writer = mockWriter();
        RateLimitGlobalFilter filter = filter(redis, writer);
        ServerWebExchange exchange = exchange(HttpMethod.POST, "/api/auth/login", "203.0.113.10");
        AtomicBoolean forwarded = new AtomicBoolean();

        filter.filter(exchange, ignored -> {
            forwarded.set(true);
            return Mono.empty();
        }).block();

        assertThat(forwarded).isTrue();
        assertThat(exchange.getResponse().getHeaders().getFirst("X-RateLimit-Limit")).isEqualTo("5");
        assertThat(exchange.getResponse().getHeaders().getFirst("X-RateLimit-Remaining")).isEqualTo("4");
    }

    @Test
    void returns429WhenTokenBucketIsExhausted() throws Exception {
        ReactiveStringRedisTemplate redis = redisReturning(List.of(0L, 0L));
        GatewayErrorResponseWriter writer = mockWriter();
        RateLimitGlobalFilter filter = filter(redis, writer);
        ServerWebExchange exchange = exchange(HttpMethod.POST, "/api/auth/login", "203.0.113.10");
        AtomicBoolean forwarded = new AtomicBoolean();

        filter.filter(exchange, ignored -> {
            forwarded.set(true);
            return Mono.empty();
        }).block();

        assertThat(forwarded).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(exchange.getResponse().getHeaders().getFirst("X-RateLimit-Remaining")).isEqualTo("0");
        verify(writer).write(
                eq(exchange),
                eq(HttpStatus.TOO_MANY_REQUESTS),
                eq("GATEWAY_RATE_LIMITED"),
                eq("Too many requests. Please try again later."));
    }

    @Test
    void failsClosedWithSafe503WhenRedisIsUnavailable() throws Exception {
        ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
        doReturn(Flux.error(new IllegalStateException("redis password and host must not leak")))
                .when(redis).execute(any(RedisScript.class), anyList(), any(Object[].class));
        GatewayErrorResponseWriter writer = mockWriter();
        RateLimitGlobalFilter filter = filter(redis, writer);
        ServerWebExchange exchange = exchange(HttpMethod.GET, "/api/employees", "203.0.113.10");

        filter.filter(exchange, ignored -> Mono.empty()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        verify(writer).write(
                eq(exchange),
                eq(HttpStatus.SERVICE_UNAVAILABLE),
                eq("RATE_LIMITER_UNAVAILABLE"),
                eq("Rate limiting is temporarily unavailable. Please retry shortly."));
    }

    @Test
    void usesDistinctPoliciesForLoginRegistrationGeneralAndReports() throws Exception {
        RateLimitGlobalFilter filter = filter(redisReturning(List.of(1L, 1L)), mockWriter());

        assertThat(filter.resolvePolicy(exchange(HttpMethod.POST, "/api/auth/login", "203.0.113.10")).name())
                .isEqualTo("login");
        assertThat(filter.resolvePolicy(exchange(HttpMethod.POST, "/api/auth/register", "203.0.113.10")).name())
                .isEqualTo("registration");
        assertThat(filter.resolvePolicy(exchange(HttpMethod.GET, "/api/reports/payroll", "203.0.113.10")).name())
                .isEqualTo("reports");
        assertThat(filter.resolvePolicy(exchange(HttpMethod.GET, "/api/employees", "203.0.113.10")).name())
                .isEqualTo("general");
        assertThat(filter.resolvePolicy(exchange(HttpMethod.GET, "/actuator/health", "203.0.113.10")))
                .isNull();
    }

    @Test
    void usesVerifiedSubjectForAuthenticatedTraffic() throws Exception {
        ReactiveStringRedisTemplate redis = redisReturning(List.of(1L, 10L));
        RateLimitGlobalFilter filter = filter(redis, mockWriter());
        ServerWebExchange exchange = exchange(HttpMethod.GET, "/api/employees", "203.0.113.10");
        exchange.getAttributes().put(
                com.ems.gateway.security.JwtAuthenticationGlobalFilter.AUTHENTICATED_SUBJECT_ATTRIBUTE,
                "employee@example.test");

        filter.filter(exchange, ignored -> Mono.empty()).block();

        @SuppressWarnings({"unchecked", "rawtypes"})
        org.mockito.ArgumentCaptor<List<String>> keys = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(redis).execute(any(RedisScript.class), keys.capture(), any(Object[].class));
        assertThat(keys.getValue()).hasSize(1);
        assertThat(keys.getValue().get(0)).startsWith("ems:gateway:rate-limit:general:client:");
        assertThat(keys.getValue().get(0)).doesNotContain("employee@example.test");
    }

    @Test
    void doesNotRateLimitCorsPreflightRequests() throws Exception {
        ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
        RateLimitGlobalFilter filter = filter(redis, mockWriter());
        ServerWebExchange exchange = exchange(HttpMethod.OPTIONS, "/api/employees", "203.0.113.10");
        AtomicBoolean forwarded = new AtomicBoolean();

        filter.filter(exchange, ignored -> {
            forwarded.set(true);
            return Mono.empty();
        }).block();

        assertThat(forwarded).isTrue();
        verify(redis, org.mockito.Mockito.never())
                .execute(any(RedisScript.class), anyList(), any(Object[].class));
    }

    private RateLimitGlobalFilter filter(
            ReactiveStringRedisTemplate redis,
            GatewayErrorResponseWriter writer) {
        return new RateLimitGlobalFilter(
                redis,
                new TrustedClientAddressKeyResolver(""),
                writer,
                2, 5, 1,
                1, 2, 1,
                20, 40, 1,
                2, 4, 1);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ReactiveStringRedisTemplate redisReturning(List<Long> result) {
        ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
        doReturn(Flux.just(result))
                .when(redis).execute(any(RedisScript.class), anyList(), any(Object[].class));
        return redis;
    }

    private GatewayErrorResponseWriter mockWriter() {
        GatewayErrorResponseWriter writer = mock(GatewayErrorResponseWriter.class);
        doAnswer(invocation -> {
            ServerWebExchange exchange = invocation.getArgument(0);
            HttpStatus status = invocation.getArgument(1);
            exchange.getResponse().setStatusCode(status);
            return exchange.getResponse().setComplete();
        }).when(writer).write(any(ServerWebExchange.class), any(HttpStatus.class),
                anyString(), anyString());
        return writer;
    }

    private ServerWebExchange exchange(HttpMethod method, String path, String remote) throws Exception {
        MockServerHttpRequest.BaseBuilder<?> builder = method == HttpMethod.POST
                ? MockServerHttpRequest.post(path)
                : method == HttpMethod.OPTIONS
                    ? MockServerHttpRequest.options(path)
                    : MockServerHttpRequest.get(path);
        return MockServerWebExchange.from(builder
                .remoteAddress(new InetSocketAddress(InetAddress.getByName(remote), 43210))
                .build());
    }
}
