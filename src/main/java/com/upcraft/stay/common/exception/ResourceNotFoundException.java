package com.upcraft.stay.common.exception;

import org.springframework.http.HttpStatus;

import com.upcraft.stay.common.enums.ErrorCode;

/**
 * Thrown when a requested entity or resource does not exist or is inaccessible.
 */
public class ResourceNotFoundException extends BaseApiException {

    public ResourceNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND);
    }
}

