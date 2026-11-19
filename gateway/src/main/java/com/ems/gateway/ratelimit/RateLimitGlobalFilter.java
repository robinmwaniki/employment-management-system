package com.ems.gateway.ratelimit;

import com.ems.gateway.error.GatewayErrorResponseWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Applies route-specific Redis token-bucket limits before requests reach the backend.
 *
 * <p>The Lua script performs token refill and consumption atomically in Redis,
 * so limits are shared across gateway instances. Redis errors fail closed with
 * a safe 503 response; the built-in Spring Cloud Redis limiter is not used here
 * because its default Redis-error fallback permits traffic.</p>
 */
@Component
@ConditionalOnProperty(
        prefix = "ems.gateway.rate-limit",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class RateLimitGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RateLimitGlobalFilter.class);

    private static final String TOKEN_BUCKET_SCRIPT_SOURCE = """
            local rate = tonumber(ARGV[1])
            local capacity = tonumber(ARGV[2])
            -- Use Redis server time so gateway instance clock skew cannot alter buckets.
            local redis_time = redis.call('TIME')
            local now = (tonumber(redis_time[1]) * 1000)
                    + math.floor(tonumber(redis_time[2]) / 1000)
            local requested = tonumber(ARGV[3])

            local fill_time = capacity / rate
            local ttl = math.max(1, math.floor(fill_time * 2))
            local last_tokens = tonumber(redis.call('HGET', KEYS[1], 'tokens'))
            if last_tokens == nil then
                last_tokens = capacity
            end
            local last_refreshed = tonumber(redis.call('HGET', KEYS[1], 'timestamp'))
            if last_refreshed == nil then
                last_refreshed = now
            end

            local delta = math.max(0, now - last_refreshed)
            local filled = math.min(capacity, last_tokens + (delta * rate / 1000))
            local allowed = filled >= requested
            local remaining = filled
            if allowed then
                remaining = filled - requested
            end

            redis.call('HSET', KEYS[1], 'tokens', remaining, 'timestamp', now)
            redis.call('EXPIRE', KEYS[1], ttl)
            return { allowed and 1 or 0, math.floor(remaining) }
            """;

    private static final RedisScript<List<Long>> TOKEN_BUCKET_SCRIPT = createScript();

    private final ReactiveStringRedisTemplate redisTemplate;
    private final TrustedClientAddressKeyResolver identityResolver;
    private final GatewayErrorResponseWriter errorResponseWriter;
    private final RateLimitPolicy loginPolicy;
    private final RateLimitPolicy registrationPolicy;
    private final RateLimitPolicy generalPolicy;
    private final RateLimitPolicy reportsPolicy;

    /**
     * Creates the gateway limiter with independently configurable policies.
     *
     * @param redisTemplate reactive Redis template used by the atomic token-bucket script
     * @param identityResolver resolves an authenticated subject or trusted client address
     * @param errorResponseWriter writes the standard privacy-safe gateway error body
     * @param loginRate login token refill rate per second
     * @param loginBurst login bucket capacity
     * @param loginTokens tokens consumed by each login request
     * @param registrationRate registration token refill rate per second
     * @param registrationBurst registration bucket capacity
     * @param registrationTokens tokens consumed by each registration request
     * @param generalRate general API token refill rate per second
     * @param generalBurst general API bucket capacity
     * @param generalTokens tokens consumed by each general API request
     * @param reportsRate reports token refill rate per second
     * @param reportsBurst reports bucket capacity
     * @param reportsTokens tokens consumed by each report request
     */
    public RateLimitGlobalFilter(
            ReactiveStringRedisTemplate redisTemplate,
            TrustedClientAddressKeyResolver identityResolver,
            GatewayErrorResponseWriter errorResponseWriter,
            @Value("${ems.gateway.rate-limit.login.replenish-rate:2}") int loginRate,
            @Value("${ems.gateway.rate-limit.login.burst-capacity:5}") int loginBurst,
            @Value("${ems.gateway.rate-limit.login.requested-tokens:1}") int loginTokens,
            @Value("${ems.gateway.rate-limit.registration.replenish-rate:1}") int registrationRate,
            @Value("${ems.gateway.rate-limit.registration.burst-capacity:2}") int registrationBurst,
            @Value("${ems.gateway.rate-limit.registration.requested-tokens:1}") int registrationTokens,
            @Value("${ems.gateway.rate-limit.general.replenish-rate:20}") int generalRate,
            @Value("${ems.gateway.rate-limit.general.burst-capacity:40}") int generalBurst,
            @Value("${ems.gateway.rate-limit.general.requested-tokens:1}") int generalTokens,
            @Value("${ems.gateway.rate-limit.reports.replenish-rate:2}") int reportsRate,
            @Value("${ems.gateway.rate-limit.reports.burst-capacity:4}") int reportsBurst,
            @Value("${ems.gateway.rate-limit.reports.requested-tokens:1}") int reportsTokens) {

        this.redisTemplate = redisTemplate;
        this.identityResolver = identityResolver;
        this.errorResponseWriter = errorResponseWriter;
        this.loginPolicy = new RateLimitPolicy("login", loginRate, loginBurst, loginTokens);
        this.registrationPolicy = new RateLimitPolicy(
                "registration", registrationRate, registrationBurst, registrationTokens);
        this.generalPolicy = new RateLimitPolicy("general", generalRate, generalBurst, generalTokens);
        this.reportsPolicy = new RateLimitPolicy("reports", reportsRate, reportsBurst, reportsTokens);
    }

    /**
     * Resolves the policy and evaluates its token bucket before forwarding.
     *
     * @param exchange current gateway exchange
     * @param chain remaining gateway filter chain
     * @return downstream completion, 429 for exhausted buckets or fail-closed 503
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        RateLimitPolicy policy = resolvePolicy(exchange);
        if (policy == null || HttpMethod.OPTIONS.equals(exchange.getRequest().getMethod())) {
            return chain.filter(exchange);
        }

        return identityResolver.resolve(exchange)
                .map(IdentityKey::new)
                .defaultIfEmpty(IdentityKey.missing())
                .flatMap(identity -> {
                    if (identity.value() == null) {
                        return reject(exchange, policy);
                    }
                    return evaluate(policy, identity.value())
                            .flatMap(decision -> {
                                if (!decision.available()) {
                                    log.warn("Redis rate limiter unavailable; rejecting policy={}", policy.name());
                                    return unavailable(exchange);
                                }
                                setRateLimitHeaders(exchange, policy, decision.remaining());
                                return decision.allowed()
                                        ? chain.filter(exchange)
                                        : reject(exchange, policy);
                            });
                });
    }

    /**
     * Runs after JWT authentication so authenticated requests are keyed by the
     * verified subject and public login requests are keyed by the client address.
     *
     * @return gateway filter order
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }

    RateLimitPolicy resolvePolicy(ServerWebExchange exchange) {
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
        if (path.startsWith("/api/")) {
            return generalPolicy;
        }
        return null;
    }

    private Mono<RateLimitDecision> evaluate(RateLimitPolicy policy, String identityKey) {
        String redisKey = "ems:gateway:rate-limit:" + policy.name() + ":" + identityKey;
        return redisTemplate.execute(
                        TOKEN_BUCKET_SCRIPT,
                        List.of(redisKey),
                        policy.replenishRate(),
                        policy.burstCapacity(),
                        policy.requestedTokens())
                .next()
                .switchIfEmpty(Mono.error(new IllegalStateException("Redis returned no rate-limit decision.")))
                .map(this::toDecision)
                .onErrorResume(exception -> Mono.just(RateLimitDecision.unavailable()));
    }

    private RateLimitDecision toDecision(List<Long> result) {
        if (result == null || result.size() < 2 || result.get(0) == null || result.get(1) == null) {
            return RateLimitDecision.unavailable();
        }
        return new RateLimitDecision(result.get(0) == 1L, true, Math.max(0, result.get(1)));
    }

    private void setRateLimitHeaders(
            ServerWebExchange exchange, RateLimitPolicy policy, long remaining) {
        exchange.getResponse().getHeaders().set("X-RateLimit-Limit",
                String.valueOf(policy.burstCapacity()));
        exchange.getResponse().getHeaders().set("X-RateLimit-Remaining",
                String.valueOf(remaining));
    }

    private Mono<Void> reject(ServerWebExchange exchange, RateLimitPolicy policy) {
        setRateLimitHeaders(exchange, policy, 0);
        return errorResponseWriter.write(
                exchange,
                HttpStatus.TOO_MANY_REQUESTS,
                "GATEWAY_RATE_LIMITED",
                "Too many requests. Please try again later.");
    }

    private Mono<Void> unavailable(ServerWebExchange exchange) {
        return errorResponseWriter.write(
                exchange,
                HttpStatus.SERVICE_UNAVAILABLE,
                "RATE_LIMITER_UNAVAILABLE",
                "Rate limiting is temporarily unavailable. Please retry shortly.");
    }

    private static RedisScript<List<Long>> createScript() {
        @SuppressWarnings({"rawtypes", "unchecked"})
        RedisScript<List<Long>> script =
                (RedisScript) new DefaultRedisScript<>(TOKEN_BUCKET_SCRIPT_SOURCE, List.class);
        return script;
    }

    record RateLimitPolicy(
            String name,
            int replenishRate,
            int burstCapacity,
            int requestedTokens) {

        RateLimitPolicy {
            if (name == null || name.isBlank()
                    || replenishRate < 1
                    || burstCapacity < 1
                    || requestedTokens < 1
                    || requestedTokens > burstCapacity) {
                throw new IllegalArgumentException("Invalid gateway rate-limit policy configuration.");
            }
        }
    }

    private record RateLimitDecision(boolean allowed, boolean available, long remaining) {

        private static RateLimitDecision unavailable() {
            return new RateLimitDecision(false, false, 0);
        }
    }

    private record IdentityKey(String value) {

        private static IdentityKey missing() {
            return new IdentityKey(null);
        }
    }
}
