package com.vuong.simplerest.core.domain.specification;

import com.vuong.simplerest.config.EntitySearchConfig;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.CriteriaBuilder;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class for building JPA Specifications based on filter maps.
 * Supports various field types including strings, booleans, numbers, enums, and
 * relationships.
 * Handles global search across searchable fields and specific field filtering.
 * Enhanced with caching for better performance.
 */
public class SpecificationBuilder {

    private static final Map<Class<?>, Map<String, Field>> FIELD_CACHE = new ConcurrentHashMap<>();

    /**
     * Builds a JPA Specification from a map of filters for the given entity class.
     * Supports global search with "search" key, relationship filtering with
     * "fieldIds" keys,
     * and direct field filtering for strings, booleans, numbers, and enums.
     * 
     * @param filters     a map of field names to filter values (e.g., "status" ->
     *                    "active", "search" -> "query")
     * @param entityClass the JPA entity class to build the specification for
     * @param <T>         the entity type
     * @return a Specification that can be used with JpaSpecificationExecutor
     */
    public static <T> Specification<T> build(Map<String, String> filters, Class<T> entityClass) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. Handle Global Search
            handleGlobalSearch(filters, entityClass, root, criteriaBuilder, predicates);

            // 2. Handle Individual Filters
            filters.forEach((key, value) -> {
                if ("search".equals(key) || !StringUtils.hasText(value))
                    return;

                if (key.endsWith("Ids") || key.endsWith("Id")) {
                    handleRelationshipFilter(key, value, entityClass, root, predicates);
                } else {
                    handleFieldFilter(key, value, entityClass, root, criteriaBuilder, predicates);
                }
            });

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static <T> void handleGlobalSearch(Map<String, String> filters, Class<T> entityClass, Root<T> root,
            CriteriaBuilder cb, List<Predicate> predicates) {
        if (filters.containsKey("search")) {
            String searchValue = filters.get("search");
            if (StringUtils.hasText(searchValue)) {
                List<String> searchableFields = EntitySearchConfig.getSearchableFields(entityClass);
                List<Predicate> orPredicates = new ArrayList<>();
                for (String field : searchableFields) {
                    orPredicates.add(cb.like(root.get(field), "%" + searchValue + "%"));
                }
                if (!orPredicates.isEmpty())
                    predicates.add(cb.or(orPredicates.toArray(new Predicate[0])));
            }
        }
    }

    private static <T> void handleRelationshipFilter(String key, String value, Class<T> entityClass, Root<T> root,
            List<Predicate> predicates) {
        String relationField = key.substring(0, key.length() - 3); // Remove "Ids"
        if (hasField(entityClass, relationField)) {
            try {
                Join<Object, Object> join = root.join(relationField, JoinType.LEFT);
                List<Long> ids = Arrays.stream(value.split(","))
                        .map(String::trim)
                        .map(Long::valueOf)
                        .toList();
                predicates.add(join.get("id").in(ids));
            } catch (Exception e) {
                // Ignore invalid relationship mapping
            }
        }
    }

    private static <T> void handleFieldFilter(String key, String value, Class<T> entityClass, Root<T> root,
            CriteriaBuilder cb, List<Predicate> predicates) {
        Field field = getCachedField(entityClass, key);
        if (field == null)
            return;

        Class<?> fieldType = field.getType();

        try {
            if (fieldType == String.class) {
                predicates.add(cb.like(root.get(key), "%" + value + "%"));
            } else if (fieldType == Boolean.class || fieldType == boolean.class) {
                predicates.add(cb.equal(root.get(key), Boolean.valueOf(value)));
            } else if (Number.class.isAssignableFrom(fieldType) || fieldType.isPrimitive()) {
                Object numberValue = parseNumber(value, fieldType);
                if (numberValue != null) {
                    predicates.add(cb.equal(root.get(key), numberValue));
                }
            } else if (fieldType.isEnum()) {
                @SuppressWarnings({ "unchecked", "rawtypes" })
                Object enumValue = Enum.valueOf((Class<Enum>) fieldType, value);
                predicates.add(cb.equal(root.get(key), enumValue));
            } else {
                predicates.add(cb.equal(root.get(key), value));
            }
        } catch (IllegalArgumentException e) {
            // Ignore invalid value conversions
        }
    }

    /**
     * Checks if the given class has a field with the specified name using cache.
     */
    private static boolean hasField(Class<?> clazz, String fieldName) {
        return getCachedField(clazz, fieldName) != null;
    }

    /**
     * Retrieves a field from specific class via cache
     */
    private static Field getCachedField(Class<?> clazz, String fieldName) {
        return FIELD_CACHE
                .computeIfAbsent(clazz, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(fieldName, k -> findFieldInHierarchy(clazz, fieldName));
    }

    private static Field findFieldInHierarchy(Class<?> clazz, String fieldName) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (field.getName().equals(fieldName)) {
                    return field;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    /**
     * Parses a string value into a number of the specified type.
     */
    private static Object parseNumber(String value, Class<?> type) {
        if (type == Integer.class || type == int.class)
            return Integer.valueOf(value);
        if (type == Long.class || type == long.class)
            return Long.valueOf(value);
        if (type == Double.class || type == double.class)
            return Double.valueOf(value);
        if (type == Float.class || type == float.class)
            return Float.valueOf(value);
        if (type == Short.class || type == short.class)
            return Short.valueOf(value);
        if (type == Byte.class || type == byte.class)
            return Byte.valueOf(value);
        return null;
    }
}
