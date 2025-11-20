package com.vuong.simplerest.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InputSanitizer Tests")
class InputSanitizerTest {

    private InputSanitizer inputSanitizer;

    @BeforeEach
    void setUp() {
        inputSanitizer = new InputSanitizer();
    }

    @Test
    @DisplayName("Should sanitize string by trimming whitespace")
    void shouldSanitizeStringByTrimming() {
        // Given
        String input = "  test string  ";

        // When
        String result = inputSanitizer.sanitizeString(input);

        // Then
        assertThat(result).isEqualTo("test string");
    }

    @Test
    @DisplayName("Should remove null bytes from string")
    void shouldRemoveNullBytes() {
        // Given
        String input = "test\0string";

        // When
        String result = inputSanitizer.sanitizeString(input);

        // Then
        assertThat(result).isEqualTo("teststring");
    }

    @Test
    @DisplayName("Should remove control characters from string")
    void shouldRemoveControlCharacters() {
        // Given
        String input = "test\u0001string\u001F";

        // When
        String result = inputSanitizer.sanitizeString(input);

        // Then
        assertThat(result).isEqualTo("teststring");
    }

    @Test
    @DisplayName("Should return null when input is null")
    void shouldReturnNullWhenInputIsNull() {
        // When
        String result = inputSanitizer.sanitizeString(null);

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should return empty string when input is empty")
    void shouldReturnEmptyWhenInputIsEmpty() {
        // When
        String result = inputSanitizer.sanitizeString("");

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should sanitize filters map")
    void shouldSanitizeFiltersMap() {
        // Given
        Map<String, String> filters = new HashMap<>();
        filters.put("name", "  john  ");
        filters.put("email", "test@test.com");

        // When
        Map<String, String> result = inputSanitizer.sanitizeFilters(filters);

        // Then
        assertThat(result)
            .hasSize(2)
            .containsEntry("name", "john")
            .containsEntry("email", "test@test.com");
    }

    @Test
    @DisplayName("Should return null when filters map is null")
    void shouldReturnNullWhenFiltersIsNull() {
        // When
        Map<String, String> result = inputSanitizer.sanitizeFilters(null);

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should detect SQL injection patterns")
    void shouldDetectSqlInjection() {
        // Given
        String sqlInjection = "SELECT * FROM users";

        // When & Then
        assertThat(inputSanitizer.containsMaliciousContent(sqlInjection)).isTrue();
    }

    @Test
    @DisplayName("Should detect XSS patterns")
    void shouldDetectXss() {
        // Given
        String xss = "<script>alert('xss')</script>";

        // When & Then
        assertThat(inputSanitizer.containsMaliciousContent(xss)).isTrue();
    }

    @Test
    @DisplayName("Should detect path traversal patterns")
    void shouldDetectPathTraversal() {
        // Given
        String pathTraversal = "../../../etc/passwd";

        // When & Then
        assertThat(inputSanitizer.containsMaliciousContent(pathTraversal)).isTrue();
    }

    @Test
    @DisplayName("Should return false for safe content")
    void shouldReturnFalseForSafeContent() {
        // Given
        String safeContent = "Hello World";

        // When & Then
        assertThat(inputSanitizer.containsMaliciousContent(safeContent)).isFalse();
    }

    @Test
    @DisplayName("Should return false when input is null for malicious content check")
    void shouldReturnFalseForNullInput() {
        // When & Then
        assertThat(inputSanitizer.containsMaliciousContent(null)).isFalse();
    }

    @Test
    @DisplayName("Should limit length in logging sanitization")
    void shouldLimitLengthInLogging() {
        // Given
        String longString = "a".repeat(2000);

        // When
        String result = inputSanitizer.sanitizeForLogging(longString);

        // Then
        assertThat(result)
            .hasSize(1003) // 1000 chars + "..."
            .endsWith("...");
    }

    @Test
    @DisplayName("Should mask sensitive information in logging")
    void shouldMaskSensitiveInfoInLogging() {
        // Given - test simpler cases first
        String passwordOnly = "password=secret123";
        String tokenOnly = "token=abc123";
        String both = "password=secret123&token=abc123";

        // When
        String result1 = inputSanitizer.sanitizeForLogging(passwordOnly);
        String result2 = inputSanitizer.sanitizeForLogging(tokenOnly);
        String result3 = inputSanitizer.sanitizeForLogging(both);

        // Then
        assertThat(result1).isEqualTo("password=***");
        assertThat(result2).isEqualTo("token=***");
        assertThat(result3)
            .contains("password=***")
            .contains("token=***");
    }
}
