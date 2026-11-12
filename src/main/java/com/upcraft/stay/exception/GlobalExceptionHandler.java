package com.upcraft.stay.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import com.upcraft.stay.common.constants.AppConstants;
import com.upcraft.stay.common.dto.ApiResponseDTO;
import com.upcraft.stay.common.enums.ErrorCode;
import com.upcraft.stay.common.exception.BaseApiException;

/**
 * Centralized REST exception mapper returning sanitized {@link ApiResponseDTO} responses with trace IDs.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BaseApiException.class)
    public ResponseEntity<ApiResponseDTO<Void>> handleBaseApiException(BaseApiException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(ApiResponseDTO.error(exception.getMessage(), exception.getErrorCode(), currentTraceId()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponseDTO<Void>> handleResponseStatus(ResponseStatusException exception) {
        HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
        ErrorCode errorCode = mapHttpStatusToErrorCode(status);
        String reason = exception.getReason() != null ? exception.getReason() : "Request failed";
        return ResponseEntity.status(exception.getStatusCode())
                .body(ApiResponseDTO.error(reason, errorCode, currentTraceId()));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponseDTO<Void>> handleBadCredentials(BadCredentialsException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponseDTO.error("Invalid email or password", ErrorCode.UNAUTHORIZED, currentTraceId()));
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ApiResponseDTO<Void>> handleDisabled(DisabledException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponseDTO.error("Account is inactive. Please contact support.", ErrorCode.FORBIDDEN, currentTraceId()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponseDTO<Void>> handleAccessDenied(AccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponseDTO.error("You do not have permission to perform this action.", ErrorCode.FORBIDDEN, currentTraceId()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponseDTO<Map<String, String>>> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        exception.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName();
            fieldErrors.put(fieldName, error.getDefaultMessage());
        });
        String summary = fieldErrors.values().stream().findFirst().orElse("Validation failed");
        return ResponseEntity.badRequest()
                .body(ApiResponseDTO.error(summary, fieldErrors, ErrorCode.VALIDATION_ERROR, currentTraceId()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponseDTO<Void>> handleGeneric(Exception exception) {
        String traceId = currentTraceId();
        log.error("Unhandled exception occurred. traceId={}", traceId, exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponseDTO.error("An unexpected error occurred.", ErrorCode.INTERNAL_ERROR, traceId));
    }

    private ErrorCode mapHttpStatusToErrorCode(HttpStatus status) {
        if (status == null) {
            return ErrorCode.INTERNAL_ERROR;
        }
        return switch (status) {
            case BAD_REQUEST -> ErrorCode.BAD_REQUEST;
            case UNAUTHORIZED -> ErrorCode.UNAUTHORIZED;
            case FORBIDDEN -> ErrorCode.FORBIDDEN;
            case NOT_FOUND -> ErrorCode.RESOURCE_NOT_FOUND;
            case CONFLICT -> ErrorCode.CONFLICT;
            case TOO_MANY_REQUESTS -> ErrorCode.RATE_LIMIT_EXCEEDED;
            default -> ErrorCode.INTERNAL_ERROR;
        };
    }

    private String currentTraceId() {
        return MDC.get(AppConstants.TRACE_ID_MDC_KEY);
    }
}
