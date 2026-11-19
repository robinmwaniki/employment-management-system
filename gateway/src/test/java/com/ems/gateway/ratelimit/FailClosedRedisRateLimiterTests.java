package com.ems.gateway.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies Redis errors cannot turn into allowed gateway requests. */
class FailClosedRedisRateLimiterTests {

    @Test
    void identifiesRedisFailureSentinel() {
        var response = new RateLimiter.Response(true,
                Map.of("X-RateLimit-Remaining", "-1"));

        assertThat(FailClosedRedisRateLimiter.isRedisFailure(response)).isTrue();
    }

    @Test
    void doesNotTreatNormalAllowedOrExhaustedBucketsAsRedisFailures() {
        var allowed = new RateLimiter.Response(true,
                Map.of("X-RateLimit-Remaining", "3"));
        var exhausted = new RateLimiter.Response(false,
                Map.of("X-RateLimit-Remaining", "0"));

        assertThat(FailClosedRedisRateLimiter.isRedisFailure(allowed)).isFalse();
        assertThat(FailClosedRedisRateLimiter.isRedisFailure(exhausted)).isFalse();
    }

    @Test
    void handlesMissingRateLimitHeadersSafely() {
        var response = new RateLimiter.Response(true, Map.of());

        assertThat(FailClosedRedisRateLimiter.isRedisFailure(response)).isFalse();
    }
}
