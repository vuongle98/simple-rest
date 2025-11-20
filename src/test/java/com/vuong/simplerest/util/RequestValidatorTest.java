package com.vuong.simplerest.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RequestValidator Tests")
class RequestValidatorTest {

    private RequestValidator requestValidator;

    @BeforeEach
    void setUp() {
        requestValidator = new RequestValidator();
    }

    @Test
    @DisplayName("Should validate filters with valid data")
    void shouldValidateValidFilters() {
        // Given
        Map<String, String> filters = Map.of("name", "John", "age", "25");

        // When
        List<String> errors = requestValidator.validateFilters(filters);

        // Then
        assertThat(errors).isEmpty();
    }

    @Test
    @DisplayName("Should detect empty filter key")
    void shouldDetectEmptyFilterKey() {
        // Given
        Map<String, String> filters = Map.of("", "value");

        // When
        List<String> errors = requestValidator.validateFilters(filters);

        // Then
        assertThat(errors)
            .hasSize(1)
            .contains("Filter key cannot be empty");
    }

    @Test
    @DisplayName("Should detect empty filter value")
    void shouldDetectEmptyFilterValue() {
        // Given
        Map<String, String> filters = Map.of("name", "");

        // When
        List<String> errors = requestValidator.validateFilters(filters);

        // Then
        assertThat(errors)
            .hasSize(1)
            .contains("Filter value cannot be empty for key: name");
    }

    @Test
    @DisplayName("Should handle null filters map")
    void shouldHandleNullFilters() {
        // When
        List<String> errors = requestValidator.validateFilters(null);

        // Then
        assertThat(errors).isEmpty();
    }

    @Test
    @DisplayName("Should validate valid ID")
    void shouldValidateValidId() {
        // Given
        Long validId = 123L;

        // When
        List<String> errors = requestValidator.validateId(validId);

        // Then
        assertThat(errors).isEmpty();
    }

    @Test
    @DisplayName("Should detect null ID")
    void shouldDetectNullId() {
        // When
        List<String> errors = requestValidator.validateId(null);

        // Then
        assertThat(errors)
            .hasSize(1)
            .contains("ID cannot be null");
    }

    @Test
    @DisplayName("Should validate valid entity")
    void shouldValidateValidEntity() {
        // Given
        Map<String, Object> entity = Map.of("name", "John", "age", 25);

        // When
        List<String> errors = requestValidator.validateEntity(entity);

        // Then
        assertThat(errors).isEmpty();
    }

    @Test
    @DisplayName("Should detect null entity")
    void shouldDetectNullEntity() {
        // When
        List<String> errors = requestValidator.validateEntity(null);

        // Then
        assertThat(errors)
            .hasSize(1)
            .contains("Entity cannot be null");
    }

    @Test
    @DisplayName("Should validate valid pageable")
    void shouldValidateValidPageable() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);

        // When
        List<String> errors = requestValidator.validatePageable(pageable);

        // Then
        assertThat(errors).isEmpty();
    }

    @Test
    @DisplayName("Should detect null pageable")
    void shouldDetectNullPageable() {
        // When
        List<String> errors = requestValidator.validatePageable(null);

        // Then
        assertThat(errors)
            .hasSize(1)
            .contains("Pageable cannot be null");
    }

    @Test
    @DisplayName("Should detect negative page number")
    void shouldDetectNegativePageNumber() {
        // When
        List<String> errors = requestValidator.validatePageable(-1, 10);

        // Then
        assertThat(errors)
            .hasSize(1)
            .contains("Page number cannot be negative");
    }

    @Test
    @DisplayName("Should detect zero page size")
    void shouldDetectZeroPageSize() {
        // When
        List<String> errors = requestValidator.validatePageable(0, 0);

        // Then
        assertThat(errors)
            .hasSize(1)
            .contains("Page size must be positive");
    }

    @Test
    @DisplayName("Should detect negative page size")
    void shouldDetectNegativePageSize() {
        // When
        List<String> errors = requestValidator.validatePageable(0, -5);

        // Then
        assertThat(errors)
            .hasSize(1)
            .contains("Page size must be positive");
    }
}
