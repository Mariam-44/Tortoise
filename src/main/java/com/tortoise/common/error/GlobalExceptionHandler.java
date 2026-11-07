package com.tortoise.common.error;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import tools.jackson.databind.exc.UnrecognizedPropertyException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> handleApi(ApiException ex, HttpServletRequest request) {
        return build(ex.code().status(), ex.code().name(), ex.getMessage(), request, ex.fieldErrors());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleInvalidBody(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ApiError.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiError.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED.name(),
                "Request has invalid fields", request, fieldErrors);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> handleBadParam(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        var fieldError = new ApiError.FieldError(ex.getName(), "has an invalid value: " + ex.getValue());
        return build(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED.name(),
                "Request has invalid parameters", request, List.of(fieldError));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        if (ex.getMostSpecificCause() instanceof UnrecognizedPropertyException unknown) {
            String field = unknown.getPropertyName();
            String hint = "status".equals(field)
                    ? "is not a field you can set. Use POST /events/{id}/publish or POST /events/{id}/cancel"
                    : "is not a known field";
            return build(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED.name(),
                    "Request has unknown fields", request, List.of(new ApiError.FieldError(field, hint)));
        }
        return build(HttpStatus.BAD_REQUEST, ErrorCode.MALFORMED_REQUEST.name(),
                "Request body is not valid JSON or has a value in the wrong format (dates must be ISO-8601, e.g. 2026-12-20T19:00:00Z)",
                request, List.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleEverythingElse(Exception ex, HttpServletRequest request) {
        // Spring's own errors (unknown route, wrong method, ...) already know their status code.
        if (ex instanceof ErrorResponse errorResponse) {
            HttpStatusCode status = errorResponse.getStatusCode();
            HttpStatus resolved = HttpStatus.resolve(status.value());
            String error = resolved != null ? resolved.name() : "ERROR";
            return build(status, error, errorResponse.getBody().getDetail(), request, List.of());
        }
        log.error("Unexpected error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR.name(),
                "Something went wrong on our side", request, List.of());
    }

    private ResponseEntity<ApiError> build(HttpStatusCode status, String error, String message,
                                           HttpServletRequest request, List<ApiError.FieldError> fieldErrors) {
        var body = new ApiError(status.value(), error, message, request.getRequestURI(),
                Instant.now(clock), fieldErrors);
        return ResponseEntity.status(status).body(body);
    }
}
