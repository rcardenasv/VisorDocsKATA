package com.visordocs.interfaces.dto;

/**
 * Unified error response format for all API errors.
 */
public record ErrorResponse(
        ErrorBody error
) {
    public record ErrorBody(
            String code,
            String message,
            String correlationId
    ) {
    }

    public static ErrorResponse of(String code, String message, String correlationId) {
        return new ErrorResponse(new ErrorBody(code, message, correlationId));
    }
}
