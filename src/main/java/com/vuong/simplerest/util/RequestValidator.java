package com.vuong.simplerest.util;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Component for validating HTTP request parameters and entities.
 * Provides validation methods for filters, IDs, entities, and pageable objects.
 * Returns lists of validation error messages.
 */
@Component
public class RequestValidator {

    /**
     * Validates filter parameters for null or empty keys and values.
     * @param filters the map of filter parameters
     * @return list of validation error messages
     */
    public List<String> validateFilters(Map<String, String> filters) {
        List<String> errors = new ArrayList<>();
        if (filters != null) {
            filters.forEach((key, value) -> {
                if (!StringUtils.hasText(key)) {
                    errors.add("Filter key cannot be empty");
                }
                if (!StringUtils.hasText(value)) {
                    errors.add("Filter value cannot be empty for key: " + key);
                }
            });
        }
        return errors;
    }

    /**
     * Validates that an ID is not null.
     * @param id the ID to validate
     * @return list of validation error messages
     */
    public List<String> validateId(Object id) {
        List<String> errors = new ArrayList<>();
        if (id == null) {
            errors.add("ID cannot be null");
        }
        return errors;
    }

    /**
     * Validates that an entity is not null.
     * @param entity the entity to validate
     * @return list of validation error messages
     */
    public List<String> validateEntity(Object entity) {
        List<String> errors = new ArrayList<>();
        if (entity == null) {
            errors.add("Entity cannot be null");
        }
        return errors;
    }

    /**
     * Validates pageable parameters for reasonable limits.
     * @param pageable the pageable object to validate
     * @return list of validation error messages
     */
    public List<String> validatePageable(Pageable pageable) {
        List<String> errors = new ArrayList<>();
        if (pageable == null) {
            errors.add("Pageable cannot be null");
        } else {
            try {
                if (pageable.getPageNumber() < 0) {
                    errors.add("Page number cannot be negative");
                }
                if (pageable.getPageSize() <= 0) {
                    errors.add("Page size must be positive");
                }
            } catch (IllegalArgumentException e) {
                // If accessing pageable properties throws exception, add generic error
                errors.add("Invalid pageable parameters: " + e.getMessage());
            }
        }
        return errors;
    }

    /**
     * Validates the pageable parameters (page and size).
     * @param page the page number (must be >= 0)
     * @param size the page size (must be > 0)
     * @return list of validation error messages, empty if parameters are valid
     */
    public List<String> validatePageable(int page, int size) {
        List<String> errors = new ArrayList<>();
        if (page < 0) {
            errors.add("Page number cannot be negative");
        }
        if (size <= 0) {
            errors.add("Page size must be positive");
        }
        return errors;
    }
} 