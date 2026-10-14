package com.smartedtech.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        List<FieldErrorItem> fieldErrors
) {
    public ErrorResponse(int status, String code, String message, String path, List<FieldErrorItem> fieldErrors) {
        this(Instant.now(), status, code, message, path, fieldErrors != null ? fieldErrors : List.of());
    }

    public ErrorResponse(int status, String code, String message, String path) {
        this(Instant.now(), status, code, message, path, List.of());
    }

    public record FieldErrorItem(String field, String message) {}
}
