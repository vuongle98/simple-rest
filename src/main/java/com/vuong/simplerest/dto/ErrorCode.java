package com.vuong.simplerest.dto;

import lombok.Getter;

/**
 * Enum representing error codes for API responses.
 * Each error code has a string identifier and a default message for client communication.
 */
@Getter
public enum ErrorCode {
    /** Internal server error occurred. */
    INTERNAL_SERVER_ERROR("INTERNAL_SERVER_ERROR", "Internal server error occurred"),
    /** Bad request. */
    BAD_REQUEST("BAD_REQUEST", "Bad request"),
    /** Unauthorized access. */
    UNAUTHORIZED("UNAUTHORIZED", "Unauthorized access"),
    /** Access forbidden. */
    FORBIDDEN("FORBIDDEN", "Access forbidden"),
    /** Resource not found. */
    NOT_FOUND("NOT_FOUND", "Resource not found"),
    /** HTTP method not allowed. */
    METHOD_NOT_ALLOWED("METHOD_NOT_ALLOWED", "HTTP method not allowed"),
    /** Resource conflict. */
    CONFLICT("CONFLICT", "Resource conflict"),
    /** Unsupported media type. */
    UNSUPPORTED_MEDIA_TYPE("UNSUPPORTED_MEDIA_TYPE", "Unsupported media type"),

    /** Validation failed. */
    VALIDATION_ERROR("VALIDATION_ERROR", "Validation failed"),
    /** Required field is missing. */
    MISSING_REQUIRED_FIELD("MISSING_REQUIRED_FIELD", "Required field is missing"),
    /** Invalid field value. */
    INVALID_FIELD_VALUE("INVALID_FIELD_VALUE", "Invalid field value"),

    /** Entity not found. */
    ENTITY_NOT_FOUND("ENTITY_NOT_FOUND", "Entity not found"),
    /** Entity already exists. */
    DUPLICATE_ENTITY("DUPLICATE_ENTITY", "Entity already exists"),
    /** Invalid operation. */
    INVALID_OPERATION("INVALID_OPERATION", "Invalid operation"),

    /** Data access error. */
    DATA_ACCESS_ERROR("DATA_ACCESS_ERROR", "Data access error"),
    /** Data constraint violation. */
    CONSTRAINT_VIOLATION("CONSTRAINT_VIOLATION", "Data constraint violation"),

    /** Configuration error. */
    CONFIGURATION_ERROR("CONFIGURATION_ERROR", "Configuration error"),
    /** Required configuration is missing. */
    MISSING_CONFIGURATION("MISSING_CONFIGURATION", "Required configuration is missing");

    /** The string identifier for the error code. */
    private final String code;
    /** The default human-readable message for the error. */
    private final String defaultMessage;

    /**
     * Constructs an ErrorCode with the specified code and default message.
     * @param code the string identifier for the error
     * @param defaultMessage the default message for the error
     */
    ErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }
}
