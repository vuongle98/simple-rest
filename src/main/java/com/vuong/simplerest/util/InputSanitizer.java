package com.vuong.simplerest.util;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Component for sanitizing user input to prevent security vulnerabilities.
 * Handles SQL injection, XSS, path traversal, and removes control characters.
 * Also provides safe logging by masking sensitive information.
 */
@Component
public class InputSanitizer {

    // Pattern to detect potentially dangerous content
    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
        "(?i)(union|select|insert|delete|update|drop|create|alter|exec|execute|script|javascript|vbscript|onload|onerror)"
    );

    private static final Pattern XSS_PATTERN = Pattern.compile(
        "(?i)(<script|<iframe|<object|<embed|<form|<input|<meta|<link|<style)"
    );

    private static final Pattern PATH_TRAVERSAL_PATTERN = Pattern.compile(
        "(\\.\\.|/\\\\|\\\\\\.\\.|%2e%2e|%2f|%5c)"
    );

    /**
     * Sanitizes a string by trimming, removing null bytes, and control characters.
     * @param input the input string to sanitize
     * @return the sanitized string, or null if input is null
     */
    public String sanitizeString(String input) {
        if (!StringUtils.hasText(input)) {
            return input;
        }

        String sanitized = input.trim();

        // Remove null bytes
        sanitized = sanitized.replace("\0", "");

        // Remove control characters except whitespace
        sanitized = sanitized.replaceAll("[\\x00-\\x1F\\x7F]", "");

        return sanitized;
    }

    /**
     * Sanitizes all values in a filters map by applying string sanitization.
     * @param filters the map of filters to sanitize
     * @return the sanitized filters map, or null if input is null
     */
    public Map<String, String> sanitizeFilters(Map<String, String> filters) {
        if (filters == null) {
            return null;
        }

        filters.replaceAll((key, value) -> sanitizeString(value));
        return filters;
    }

    /**
     * Checks if the input string contains potentially malicious content.
     * Detects SQL injection, XSS, and path traversal patterns.
     * @param input the string to check
     * @return true if malicious content is detected, false otherwise
     */
    public boolean containsMaliciousContent(String input) {
        if (!StringUtils.hasText(input)) {
            return false;
        }

        return SQL_INJECTION_PATTERN.matcher(input).find() ||
               XSS_PATTERN.matcher(input).find() ||
               PATH_TRAVERSAL_PATTERN.matcher(input).find();
    }

    /**
     * Sanitizes a string for safe logging by limiting length and masking sensitive information.
     * Masks passwords, tokens, secrets, and keys.
     * @param input the string to sanitize for logging
     * @return the sanitized string for logging, or null if input is null
     */
    public String sanitizeForLogging(String input) {
        if (!StringUtils.hasText(input)) {
            return input;
        }

        // For logging, limit length and remove sensitive information
        String sanitized = input;
        if (sanitized.length() > 1000) {
            sanitized = sanitized.substring(0, 1000) + "...";
        }

        // Remove potential passwords, tokens, etc.
        sanitized = sanitized.replaceAll("(?i)password=[^&\\s]+", "password=***");
        sanitized = sanitized.replaceAll("(?i)token=[^&\\s]+", "token=***");
        sanitized = sanitized.replaceAll("(?i)secret=[^&\\s]+", "secret=***");
        sanitized = sanitized.replaceAll("(?i)key=[^&\\s]+", "key=***");

        return sanitized;
    }
}
