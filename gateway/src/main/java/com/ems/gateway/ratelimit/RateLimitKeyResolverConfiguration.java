package com.ems.gateway.ratelimit;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Creates independent key namespaces for overlapping default and route-specific
 * quotas so one policy cannot consume another policy's Redis token bucket.
 */
@Configuration
public class RateLimitKeyResolverConfiguration {

    /**
     * Creates the login quota resolver with a separate Redis key namespace.
     *
     * @param resolver shared privacy-safe identity resolver
     * @return login-specific resolver
     */
    @Bean
    public KeyResolver emsLoginRateLimitKeyResolver(EmsRateLimitKeyResolver resolver) {
        return exchange -> resolver.resolve(exchange).map(key -> "login:" + key);
    }

    /**
     * Creates the registration quota resolver with a separate Redis key namespace.
     *
     * @param resolver shared privacy-safe identity resolver
     * @return registration-specific resolver
     */
    @Bean
    public KeyResolver emsRegistrationRateLimitKeyResolver(EmsRateLimitKeyResolver resolver) {
        return exchange -> resolver.resolve(exchange).map(key -> "registration:" + key);
    }

    /**
     * Creates the report quota resolver with a separate Redis key namespace.
     *
     * @param resolver shared privacy-safe identity resolver
     * @return report-specific resolver
     */
    @Bean
    public KeyResolver emsReportsRateLimitKeyResolver(EmsRateLimitKeyResolver resolver) {
        return exchange -> resolver.resolve(exchange).map(key -> "reports:" + key);
    }
}
