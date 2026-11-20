package com.ems.gateway.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Logs circuit-breaker state transitions without request or user data.
 */
@Component
public class CircuitBreakerStateTransitionLogger {

    private static final Logger log =
            LoggerFactory.getLogger(CircuitBreakerStateTransitionLogger.class);

    /**
     * Subscribes to existing and subsequently registered circuit breakers.
     *
     * @param registry Resilience4j circuit-breaker registry
     */
    public CircuitBreakerStateTransitionLogger(CircuitBreakerRegistry registry) {
        registry.getAllCircuitBreakers().forEach(this::register);
        registry.getEventPublisher().onEntryAdded(event -> register(event.getAddedEntry()));
    }

    private void register(CircuitBreaker circuitBreaker) {
        circuitBreaker.getEventPublisher().onStateTransition(event ->
                log.info("Gateway circuit breaker state changed: name={}, transition={}",
                        event.getCircuitBreakerName(), event.getStateTransition()));
    }
}
