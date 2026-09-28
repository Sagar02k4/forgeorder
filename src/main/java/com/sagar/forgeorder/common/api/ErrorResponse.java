package com.sagar.forgeorder.common.api;

import java.time.Instant;

public record ErrorResponse(
        String code,
        String message,
        String correlationId,
        Instant timestamp
) {
    public ErrorResponse(String code, String message, String correlationId) {
        this(code, message, correlationId, Instant.now());
    }
}