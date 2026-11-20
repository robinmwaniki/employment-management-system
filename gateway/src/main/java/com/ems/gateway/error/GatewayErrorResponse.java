package com.ems.gateway.error;

import java.time.Instant;

/**
 * Stable, privacy-safe error representation returned by the gateway.
 *
 * @param timestamp UTC time at which the gateway produced the response
 * @param status HTTP status code
 * @param code stable machine-readable gateway error code
 * @param message safe message intended for API clients
 * @param correlationId request correlation identifier, when available
 */
public record GatewayErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        String correlationId) {
}
