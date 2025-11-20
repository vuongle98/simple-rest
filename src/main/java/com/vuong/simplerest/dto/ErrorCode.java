package com.vuong.simplerest.dto;

public enum ErrorCode {
    // General errors
    INTERNAL_SERVER_ERROR("INTERNAL_SERVER_ERROR", "Internal server error occurred"),
    BAD_REQUEST("BAD_REQUEST", "Bad request"),
    UNAUTHORIZED("UNAUTHORIZED", "Unauthorized access"),
    FORBIDDEN("FORBIDDEN", "Access forbidden"),
    NOT_FOUND("NOT_FOUND", "Resource not found"),
    METHOD_NOT_ALLOWED("METHOD_NOT_ALLOWED", "HTTP method not allowed"),
    CONFLICT("CONFLICT", "Resource conflict"),
    UNSUPPORTED_MEDIA_TYPE("UNSUPPORTED_MEDIA_TYPE", "Unsupported media type"),

    // Validation errors
    VALIDATION_ERROR("VALIDATION_ERROR", "Validation failed"),
    MISSING_REQUIRED_FIELD("MISSING_REQUIRED_FIELD", "Required field is missing"),
    INVALID_FIELD_VALUE("INVALID_FIELD_VALUE", "Invalid field value"),

    // Business logic errors
    ENTITY_NOT_FOUND("ENTITY_NOT_FOUND", "Entity not found"),
    DUPLICATE_ENTITY("DUPLICATE_ENTITY", "Entity already exists"),
    INVALID_OPERATION("INVALID_OPERATION", "Invalid operation"),

    // Data access errors
    DATA_ACCESS_ERROR("DATA_ACCESS_ERROR", "Data access error"),
    CONSTRAINT_VIOLATION("CONSTRAINT_VIOLATION", "Data constraint violation"),

    // Configuration errors
    CONFIGURATION_ERROR("CONFIGURATION_ERROR", "Configuration error"),
    MISSING_CONFIGURATION("MISSING_CONFIGURATION", "Required configuration is missing");

    private final String code;
    private final String defaultMessage;

    ErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public String getCode() {
        return code;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
