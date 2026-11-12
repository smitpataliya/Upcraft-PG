package com.upcraft.stay.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import com.upcraft.stay.common.enums.ErrorCode;

/**
 * Base runtime exception for domain and authorization errors mapped by {@code GlobalExceptionHandler}.
 */
@Getter
public abstract class BaseApiException extends RuntimeException {

    private final HttpStatus status;
    private final ErrorCode errorCode;

    protected BaseApiException(String message, HttpStatus status, ErrorCode errorCode) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    protected BaseApiException(String message, Throwable cause, HttpStatus status, ErrorCode errorCode) {
        super(message, cause);
        this.status = status;
        this.errorCode = errorCode;
    }
}

