package com.upcraft.stay.common.dto;

import java.time.Instant;

import com.upcraft.stay.common.enums.ErrorCode;

/**
 * Standard envelope for all {@code /api/v1/**} responses and global error payloads.
 *
 * @param <T> response payload type
 */
public record ApiResponseDTO<T>(
        boolean success,
        String message,
        T data,
        ErrorCode errorCode,
        String traceId,
        Instant timestamp
) {

    /**
     * Builds a successful API response wrapper.
     *
     * @param message human-readable confirmation
     * @param data    response payload (nullable)
     * @param traceId request correlation identifier
     * @param <T>     payload type
     * @return wrapped success response
     */
    public static <T> ApiResponseDTO<T> success(String message, T data, String traceId) {
        return new ApiResponseDTO<>(true, message, data, null, traceId, Instant.now());
    }

    /**
     * Builds an error API response wrapper without internal stack details.
     *
     * @param message   safe client-facing error description
     * @param errorCode standardized error classification
     * @param traceId   request correlation identifier
     * @param <T>       payload type
     * @return wrapped error response
     */
    public static <T> ApiResponseDTO<T> error(String message, ErrorCode errorCode, String traceId) {
        return new ApiResponseDTO<>(false, message, null, errorCode, traceId, Instant.now());
    }

    /**
     * Builds an error API response wrapper with structured field details.
     *
     * @param message   safe client-facing error description
     * @param data      validation or structured error metadata
     * @param errorCode standardized error classification
     * @param traceId   request correlation identifier
     * @param <T>       payload type
     * @return wrapped error response
     */
    public static <T> ApiResponseDTO<T> error(String message, T data, ErrorCode errorCode, String traceId) {
        return new ApiResponseDTO<>(false, message, data, errorCode, traceId, Instant.now());
    }
}

