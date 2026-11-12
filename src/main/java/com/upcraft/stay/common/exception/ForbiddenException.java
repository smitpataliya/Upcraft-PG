package com.upcraft.stay.common.exception;

import org.springframework.http.HttpStatus;

import com.upcraft.stay.common.enums.ErrorCode;

/**
 * Thrown when an authenticated user lacks role, organization, or property permission.
 */
public class ForbiddenException extends BaseApiException {

    public ForbiddenException(String message) {
        super(message, HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN);
    }
}

