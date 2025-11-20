package com.vuong.simplerest.dto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ErrorResponse Tests")
class ErrorResponseTest {

    @Test
    @DisplayName("Should create ErrorResponse with constructor")
    void shouldCreateWithConstructor() {
        // Given
        int status = 400;
        String error = "Bad Request";
        String message = "Invalid input";
        String path = "/api/test";

        // When
        ErrorResponse response = new ErrorResponse(status, error, message, path);

        // Then
        assertThat(response.getStatus()).isEqualTo(status);
        assertThat(response.getError()).isEqualTo(error);
        assertThat(response.getMessage()).isEqualTo(message);
        assertThat(response.getPath()).isEqualTo(path);
        assertThat(response.getTimestamp()).isNotNull();
        assertThat(response.getFieldErrors()).isNull();
        assertThat(response.getDetails()).isNull();
    }

    @Test
    @DisplayName("Should create ErrorResponse with field errors")
    void shouldCreateWithFieldErrors() {
        // Given
        int status = 400;
        String error = "Bad Request";
        String message = "Validation failed";
        String path = "/api/test";
        List<ErrorResponse.FieldError> fieldErrors = List.of(
            new ErrorResponse.FieldError("name", "John123", "Name must be alphabetic"),
            new ErrorResponse.FieldError("age", "150", "Age must be less than 120")
        );

        // When
        ErrorResponse response = new ErrorResponse(status, error, message, path, fieldErrors);

        // Then
        assertThat(response.getStatus()).isEqualTo(status);
        assertThat(response.getError()).isEqualTo(error);
        assertThat(response.getMessage()).isEqualTo(message);
        assertThat(response.getPath()).isEqualTo(path);
        assertThat(response.getTimestamp()).isNotNull();
        assertThat(response.getFieldErrors()).hasSize(2);
        assertThat(response.getDetails()).isNull();
    }

    @Test
    @DisplayName("Should create ErrorResponse with all fields")
    void shouldCreateWithAllFields() {
        // Given
        LocalDateTime timestamp = LocalDateTime.now();
        int status = 500;
        String error = "Internal Server Error";
        String message = "Something went wrong";
        String path = "/api/test";
        List<ErrorResponse.FieldError> fieldErrors = List.of(
            new ErrorResponse.FieldError("field1", "value1", "Error message")
        );

        // When
        ErrorResponse response = new ErrorResponse(timestamp, status, error, message, path, fieldErrors, null);

        // Then
        assertThat(response.getTimestamp()).isEqualTo(timestamp);
        assertThat(response.getStatus()).isEqualTo(status);
        assertThat(response.getError()).isEqualTo(error);
        assertThat(response.getMessage()).isEqualTo(message);
        assertThat(response.getPath()).isEqualTo(path);
        assertThat(response.getFieldErrors()).isEqualTo(fieldErrors);
        assertThat(response.getDetails()).isNull();
    }

    @Test
    @DisplayName("Should create FieldError with constructor")
    void shouldCreateFieldError() {
        // Given
        String field = "name";
        String rejectedValue = "John123";
        String message = "Name must be alphabetic";

        // When
        ErrorResponse.FieldError fieldError = new ErrorResponse.FieldError(field, rejectedValue, message);

        // Then
        assertThat(fieldError.getField()).isEqualTo(field);
        assertThat(fieldError.getRejectedValue()).isEqualTo(rejectedValue);
        assertThat(fieldError.getMessage()).isEqualTo(message);
    }

    @Test
    @DisplayName("Should handle null rejected value in FieldError")
    void shouldHandleNullRejectedValue() {
        // Given
        String field = "name";
        String rejectedValue = null;
        String message = "Name is required";

        // When
        ErrorResponse.FieldError fieldError = new ErrorResponse.FieldError(field, rejectedValue, message);

        // Then
        assertThat(fieldError.getField()).isEqualTo(field);
        assertThat(fieldError.getRejectedValue()).isNull();
        assertThat(fieldError.getMessage()).isEqualTo(message);
    }
}
