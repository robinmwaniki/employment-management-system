package com.ems.gateway.error;

import com.ems.gateway.filter.CorrelationIdGlobalFilter;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Serializes consistent gateway error responses without exposing exception details.
 */
@Component
public class GatewayErrorResponseWriter {

    private final JsonMapper jsonMapper;

    /**
     * Creates the writer used by gateway filters and exception handling.
     *
     * @param jsonMapper configured Jackson 3 JSON mapper
     */
    public GatewayErrorResponseWriter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    /**
     * Writes a safe JSON error body and standard no-store response headers.
     *
     * @param exchange current server exchange
     * @param status HTTP status to return
     * @param code stable machine-readable error code
     * @param message safe client-facing message
     * @return response completion signal
     */
    public Mono<Void> write(
            ServerWebExchange exchange, HttpStatus status, String code, String message) {
        String correlationId = exchange.getResponse().getHeaders()
                .getFirst(CorrelationIdGlobalFilter.CORRELATION_ID_HEADER);
        if (correlationId == null) {
            correlationId = exchange.getRequest().getHeaders()
                    .getFirst(CorrelationIdGlobalFilter.CORRELATION_ID_HEADER);
        }

        GatewayErrorResponse body = new GatewayErrorResponse(
                Instant.now(), status.value(), code, message, correlationId);

        final byte[] payload;
        try {
            payload = jsonMapper.writeValueAsBytes(body);
        } catch (JacksonException exception) {
            return Mono.error(exception);
        }

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().setCacheControl("no-store");
        return exchange.getResponse().writeWith(
                Mono.just(exchange.getResponse().bufferFactory().wrap(payload)));
    }
}
