package com.vuong.simplerest.config;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class for configuring entity search functionality.
 * Provides methods to retrieve searchable fields from entity classes,
 * caching results to avoid repeated reflection costs.
 */
public class EntitySearchConfig {
    // Cache to avoid repeated reflection cost
    private static final Map<Class<?>, List<String>> cache = new ConcurrentHashMap<>();

    /**
     * Retrieves the list of searchable fields for a given entity class.
     * Searchable fields are those of type String. Results are cached for performance.
     * @param entityClass the entity class to inspect
     * @return list of field names that are strings
     */
    public static List<String> getSearchableFields(Class<?> entityClass) {
        return cache.computeIfAbsent(entityClass, cls -> {
            List<String> fields = new ArrayList<>();
            for (Field f : getAllFields(cls)) {
                if (f.getType().equals(String.class)) {
                    fields.add(f.getName());
                }
            }
            return fields;
        });
    }

    /**
     * Helper method to get all fields from a class, including inherited ones.
     * @param type the class to inspect
     * @return list of all fields
     */
    private static List<Field> getAllFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            fields.addAll(Arrays.asList(c.getDeclaredFields()));
        }
        return fields;
    }
}