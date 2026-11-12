package com.upcraft.stay.common.enums;

/**
 * Standardized API error codes returned in {@code ApiResponseDTO} error payloads.
 */
public enum ErrorCode {
    BAD_REQUEST,
    VALIDATION_ERROR,
    UNAUTHORIZED,
    FORBIDDEN,
    RESOURCE_NOT_FOUND,
    CONFLICT,
    RATE_LIMIT_EXCEEDED,
    INTERNAL_ERROR
}

