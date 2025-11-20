package com.vuong.simplerest.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * DTO representing an error response for API calls.
 * Provides detailed information about errors including status, message, path, and validation field errors.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {
    /** Timestamp when the error occurred. */
    private LocalDateTime timestamp; 
    /** HTTP status code of the error. */
    private int status;
    /** Error type or category. */
    private String error;
    private String message;
    /** Request path that caused the error. */
    private String path;
    /** List of field-specific validation errors. */
    private List<FieldError> fieldErrors;
    /** Additional error details as key-value pairs. */
    private Map<String, Object> details;

    /**
     * Represents a validation error for a specific field.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldError {
        /** Name of the field that failed validation. */
        private String field;
        /** The rejected value that caused the validation error. */
        private String rejectedValue;
        /** Validation error message for the field. */
        private String message;
    }

    /**
     * Constructs an ErrorResponse with basic error information.
     * @param status HTTP status code
     * @param error error type or category
     * @param message human-readable error message
     * @param path request path that caused the error
     */
    public ErrorResponse(int status, String error, String message, String path) {
        this.timestamp = LocalDateTime.now();
        this.status = status;
        this.error = error;
        this.message = message;
        this.path = path;
    }

    /**
     * Constructs an ErrorResponse with basic error information and field errors.
     * @param status HTTP status code
     * @param error error type or category
     * @param message human-readable error message
     * @param path request path that caused the error
     * @param fieldErrors list of field-specific validation errors
     */
    public ErrorResponse(int status, String error, String message, String path, List<FieldError> fieldErrors) {
        this(status, error, message, path);
        this.fieldErrors = fieldErrors;
    }
}
