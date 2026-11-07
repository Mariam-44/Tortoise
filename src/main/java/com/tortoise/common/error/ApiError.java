package com.tortoise.common.error;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The one error format every endpoint returns.
 */
public record ApiError(
        int status,
        String error,
        String message,
        String path,
        Instant timestamp,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<FieldError> fieldErrors) {

    public record FieldError(String field, String message) {
    }
}
