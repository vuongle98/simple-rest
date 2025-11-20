package com.vuong.simplerest.util;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.regex.Pattern;

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

    public Map<String, String> sanitizeFilters(Map<String, String> filters) {
        if (filters == null) {
            return null;
        }

        filters.replaceAll((key, value) -> sanitizeString(value));
        return filters;
    }

    public boolean containsMaliciousContent(String input) {
        if (!StringUtils.hasText(input)) {
            return false;
        }

        return SQL_INJECTION_PATTERN.matcher(input).find() ||
               XSS_PATTERN.matcher(input).find() ||
               PATH_TRAVERSAL_PATTERN.matcher(input).find();
    }

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
