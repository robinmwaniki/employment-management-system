package com.ems.gateway.ratelimit;

import com.ems.gateway.error.GatewayErrorResponseWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Applies distributed Redis token-bucket quotas before gateway routing.
 *
 * <p>The Lua script performs refill, token consumption and expiry atomically
 * using Redis server time. Redis errors are deliberately propagated to the
 * global error handler so the gateway fails closed with HTTP 503.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class EmsRedisRateLimitGlobalFilter implements GlobalFilter, Ordered {

    private static final String RATE_LIMIT_SCRIPT_TEXT = """
            local bucket = KEYS[1]
            local rate = tonumber(ARGV[1])
            local capacity = tonumber(ARGV[2])
            local requested = tonumber(ARGV[3])
            local clock = redis.call('TIME')
            local now = tonumber(clock[1]) * 1000 + math.floor(tonumber(clock[2]) / 1000)
            local values = redis.call('HMGET', bucket, 'tokens', 'timestamp')
            local tokens = tonumber(values[1])
            local last = tonumber(values[2])

            if tokens == nil then
              tokens = capacity
              last = now
            end

            local elapsed = math.max(0, (now - last) / 1000)
            local available = math.min(capacity, tokens + elapsed * rate)
            local allowed = 0
            if available >= requested then
              available = available - requested
              allowed = 1
            end

            local ttl = math.max(1, math.ceil((capacity / rate) * 2))
            redis.call('HSET', bucket, 'tokens', available, 'timestamp', now)
            redis.call('EXPIRE', bucket, ttl)
            return allowed
            """;

    private static final RedisScript<Long> RATE_LIMIT_SCRIPT =
            new DefaultRedisScript<>(RATE_LIMIT_SCRIPT_TEXT, Long.class);

    private final ReactiveStringRedisTemplate redisTemplate;
    private final EmsRateLimitKeyResolver keyResolver;
    private final GatewayErrorResponseWriter errorResponseWriter;
    private final Policy generalPolicy;
    private final Policy loginPolicy;
    private final Policy registrationPolicy;
    private final Policy reportsPolicy;

    /**
     * Creates the distributed limiter and loads each category's independent quota.
     *
     * @param redisTemplate reactive Redis access
     * @param keyResolver privacy-safe user/IP identity resolver
     * @param errorResponseWriter shared structured error writer
     * @param apiRate general API refill rate per second
     * @param apiBurst general API bucket capacity
     * @param loginRate login refill rate per second
     * @param loginBurst login bucket capacity
     * @param registrationRate registration refill rate per second
     * @param registrationBurst registration bucket capacity
     * @param reportsRate reports refill rate per second
     * @param reportsBurst reports bucket capacity
     */
    public EmsRedisRateLimitGlobalFilter(
            ReactiveStringRedisTemplate redisTemplate,
            EmsRateLimitKeyResolver keyResolver,
            GatewayErrorResponseWriter errorResponseWriter,
            @Value("${EMS_API_RATE_LIMIT_REPLENISH_RATE:10}") int apiRate,
            @Value("${EMS_API_RATE_LIMIT_BURST_CAPACITY:20}") int apiBurst,
            @Value("${EMS_LOGIN_RATE_LIMIT_REPLENISH_RATE:1}") int loginRate,
            @Value("${EMS_LOGIN_RATE_LIMIT_BURST_CAPACITY:5}") int loginBurst,
            @Value("${EMS_REGISTER_RATE_LIMIT_REPLENISH_RATE:1}") int registrationRate,
            @Value("${EMS_REGISTER_RATE_LIMIT_BURST_CAPACITY:3}") int registrationBurst,
            @Value("${EMS_REPORTS_RATE_LIMIT_REPLENISH_RATE:2}") int reportsRate,
            @Value("${EMS_REPORTS_RATE_LIMIT_BURST_CAPACITY:4}") int reportsBurst) {
        this.redisTemplate = redisTemplate;
        this.keyResolver = keyResolver;
        this.errorResponseWriter = errorResponseWriter;
        this.generalPolicy = new Policy("api", apiRate, apiBurst);
        this.loginPolicy = new Policy("login", loginRate, loginBurst);
        this.registrationPolicy = new Policy("registration", registrationRate, registrationBurst);
        this.reportsPolicy = new Policy("reports", reportsRate, reportsBurst);
    }

    /**
     * Ensures no quota can accidentally disable throttling or divide by zero.
     */
    @PostConstruct
    void validatePolicies() {
        generalPolicy.validate();
        loginPolicy.validate();
        registrationPolicy.validate();
        reportsPolicy.validate();
    }

    /**
     * Checks the applicable identity bucket before forwarding a routed request.
     *
     * @param exchange current gateway request
     * @param chain remaining gateway filter chain
     * @return downstream completion, structured 429, or propagated Redis failure
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (HttpMethod.OPTIONS.equals(exchange.getRequest().getMethod())) {
            return chain.filter(exchange);
        }

        Policy policy = policyFor(exchange);
        return keyResolver.resolve(exchange)
                .switchIfEmpty(Mono.error(new IllegalStateException(
                        "A rate-limit identity could not be resolved.")))
                .flatMap(identity -> isAllowed(policy, identity)
                        .flatMap(allowed -> allowed
                                ? chain.filter(exchange)
                                : errorResponseWriter.write(
                                        exchange,
                                        HttpStatus.TOO_MANY_REQUESTS,
                                        "GATEWAY_RATE_LIMITED",
                                        "Too many requests. Please try again later.")));
    }

    /**
     * Places the rate-limit check after JWT validation and before route forwarding.
     *
     * @return gateway global filter order
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }

    private Policy policyFor(ServerWebExchange exchange) {
        String path = exchange.getRequest().getURI().getPath();
        HttpMethod method = exchange.getRequest().getMethod();

        if (HttpMethod.POST.equals(method) && "/api/auth/login".equals(path)) {
            return loginPolicy;
        }
        if (HttpMethod.POST.equals(method) && "/api/auth/register".equals(path)) {
            return registrationPolicy;
        }
        if ("/api/reports".equals(path) || path.startsWith("/api/reports/")) {
            return reportsPolicy;
        }
        return generalPolicy;
    }

    private Mono<Boolean> isAllowed(Policy policy, String identity) {
        String bucketHash = sha256(policy.name() + ":" + identity);
        String redisKey = "ems:gateway:rate-limit:{" + bucketHash + "}";

        return redisTemplate.execute(
                        RATE_LIMIT_SCRIPT,
                        java.util.List.of(redisKey),
                        Integer.toString(policy.replenishRate()),
                        Integer.toString(policy.burstCapacity()),
                        "1")
                .single()
                .map(result -> result != null && result == 1L);
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }

    private record Policy(String name, int replenishRate, int burstCapacity) {

        private void validate() {
            if (replenishRate < 1 || burstCapacity < 1) {
                throw new IllegalStateException(
                        "EMS gateway rate-limit replenish rates and burst capacities must be positive.");
            }
        }
    }
}
