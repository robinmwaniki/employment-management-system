package com.ems.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that concurrent-write failures are returned as safe HTTP conflicts.
 */
class GlobalExceptionHandlerConcurrencyTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void optimisticLockFailure_returnsConflictWithoutEntityDetails() {
        var response = handler.handleConcurrentWrite(
                new ObjectOptimisticLockingFailureException("LeaveRequestEntity", 42L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getMessage())
                .isEqualTo("The record was changed by another request. Refresh and try again.");
        assertThat(response.getBody().getMessage()).doesNotContain("42");
    }

    @Test
    void uniqueConstraintFailure_returnsConflict() {
        var response = handler.handleConcurrentWrite(
                new DataIntegrityViolationException("duplicate key"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getMessage()).doesNotContain("duplicate key");
    }
}
