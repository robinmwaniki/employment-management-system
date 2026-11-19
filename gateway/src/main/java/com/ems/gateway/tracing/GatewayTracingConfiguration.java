package com.ems.gateway.tracing;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Applies privacy controls to Micrometer observations before they are exported
 * as spans. Route IDs remain available for diagnosis, while raw URLs and
 * internal backend addresses are removed.
 */
@Configuration
public class GatewayTracingConfiguration {

    /**
     * Removes high-cardinality URL and credential-related attributes from
     * observations. Spring Cloud Gateway's default client observation includes
     * the full request URI as a high-cardinality span attribute.
     *
     * @return observation filter that removes sensitive URL/header attributes
     */
    @Bean
    public ObservationFilter gatewayTracePrivacyFilter() {
        return context -> {
            context.removeHighCardinalityKeyValues(
                    "http.uri",
                    "url.full",
                    "url.query",
                    "http.target",
                    "http.request.header.authorization",
                    "http.request.header.cookie",
                    "http.request.header.set-cookie",
                    "http.request.header.x-ems-management-key",
                    "http.request.header.x-forwarded-for",
                    "http.request.header.x-correlation-id");
            // Keep the configured route ID, but do not export internal backend
            // addresses or service hostnames as observation attributes.
            context.removeLowCardinalityKeyValue("spring.cloud.gateway.route.uri");
            return context;
        };
    }
}
