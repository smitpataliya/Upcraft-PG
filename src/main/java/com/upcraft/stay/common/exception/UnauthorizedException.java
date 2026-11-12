package com.upcraft.stay.common.exception;

import org.springframework.http.HttpStatus;

import com.upcraft.stay.common.enums.ErrorCode;

/**
 * Thrown when authentication fails or credentials/tokens are invalid.
 */
public class UnauthorizedException extends BaseApiException {

    public UnauthorizedException(String message) {
        super(message, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED);
    }
}

