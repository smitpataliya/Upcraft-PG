package com.upcraft.stay.common.exception;

import org.springframework.http.HttpStatus;

import com.upcraft.stay.common.enums.ErrorCode;

/**
 * Thrown when business or request validation fails.
 */
public class ValidationException extends BaseApiException {

    public ValidationException(String message) {
        super(message, HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR);
    }
}

