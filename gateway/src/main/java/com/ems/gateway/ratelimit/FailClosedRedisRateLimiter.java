package com.ems.gateway.ratelimit;

import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.support.ConfigurationService;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Redis rate limiter that denies requests when Redis reports an execution failure.
 *
 * <p>The upstream RedisRateLimiter intentionally fails open and reports a remaining
 * token count of -1 when Redis cannot evaluate a bucket. This subclass converts that
 * sentinel into a denied decision so the gateway never bypasses configured limits.</p>
 */
@Component("redisRateLimiter")
public class FailClosedRedisRateLimiter extends RedisRateLimiter {

    private static final String REMAINING_HEADER = "X-RateLimit-Remaining";

    /**
     * Creates the Redis limiter using Spring Cloud Gateway's configured script and template.
     *
     * @param redisTemplate reactive Redis template
     * @param script atomic Redis token-bucket script
     * @param configurationService gateway configuration service
     */
    public FailClosedRedisRateLimiter(
            ReactiveStringRedisTemplate redisTemplate,
            RedisScript<List<Long>> script,
            ConfigurationService configurationService) {
        super(redisTemplate, script, configurationService);
        setIncludeHeaders(true);
    }

    /**
     * Converts Redis execution failures into a denied rate-limit decision.
     *
     * @param routeId gateway route identifier
     * @param id privacy-preserving client key
     * @return allowed decision only when Redis evaluated the bucket successfully
     */
    @Override
    public Mono<RateLimiter.Response> isAllowed(String routeId, String id) {
        return super.isAllowed(routeId, id).map(response ->
                isRedisFailure(response)
                        ? new RateLimiter.Response(false, response.getHeaders())
                        : response);
    }

    /**
     * Identifies the failure sentinel emitted by Spring Cloud Gateway's Redis limiter.
     *
     * @param response token-bucket response
     * @return true when Redis could not evaluate the bucket
     */
    static boolean isRedisFailure(RateLimiter.Response response) {
        Map<String, String> headers = response.getHeaders();
        return headers != null && "-1".equals(headers.get(REMAINING_HEADER));
    }
}
