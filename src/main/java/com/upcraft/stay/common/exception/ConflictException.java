package com.upcraft.stay.common.exception;

import org.springframework.http.HttpStatus;

import com.upcraft.stay.common.enums.ErrorCode;

/**
 * Thrown when an operation conflicts with existing unique or state constraints.
 */
public class ConflictException extends BaseApiException {

    public ConflictException(String message) {
        super(message, HttpStatus.CONFLICT, ErrorCode.CONFLICT);
    }
}

