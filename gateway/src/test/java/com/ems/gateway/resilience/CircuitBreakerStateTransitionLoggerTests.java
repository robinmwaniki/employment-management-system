package com.ems.gateway.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies state-transition listeners attach to dynamically registered breakers. */
class CircuitBreakerStateTransitionLoggerTests {

    @Test
    void subscribesToCircuitBreakersRegisteredAfterStartup() {
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();
        new CircuitBreakerStateTransitionLogger(registry);

        CircuitBreaker circuitBreaker = registry.circuitBreaker("employees");
        circuitBreaker.transitionToOpenState();

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }
}
