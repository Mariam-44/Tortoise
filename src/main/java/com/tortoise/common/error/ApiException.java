package com.tortoise.common.error;

import java.util.List;

/**
 * Thrown by business logic; turned into the standard error body by {@link GlobalExceptionHandler}.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final List<ApiError.FieldError> fieldErrors;

    public ApiException(ErrorCode code, String message) {
        this(code, message, List.of());
    }

    public ApiException(ErrorCode code, String message, List<ApiError.FieldError> fieldErrors) {
        super(message);
        this.code = code;
        this.fieldErrors = List.copyOf(fieldErrors);
    }

    public ErrorCode code() {
        return code;
    }

    public List<ApiError.FieldError> fieldErrors() {
        return fieldErrors;
    }
}
