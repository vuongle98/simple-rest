package com.vuong.simplerest.core.security;

import com.vuong.simplerest.annotation.UserIdField;
import com.vuong.simplerest.config.SimpleRestProperties;
import com.vuong.simplerest.util.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Security filter component that enforces user-based data access by adding
 * user ID filtering to all database queries.
 */
@Component
public class UserIdSecurityFilter {
    
    private static final Logger logger = LoggerFactory.getLogger(UserIdSecurityFilter.class);
    
    @Autowired
    private SimpleRestProperties properties;
    
    // Cache for user ID field names per entity class
    private final Map<Class<?>, String> userIdFieldCache = new ConcurrentHashMap<>();
    
    /**
     * Creates a specification that filters entities by the current user's ID.
     * 
     * @param <T> the entity type
     * @param entityClass the entity class
     * @return a specification that filters by user ID
     */
    public <T> Specification<T> createUserIdSpecification(Class<T> entityClass) {
        // Check if security is enabled
        if (!properties.getSecurity().isEnabled()) {
            return (root, query, cb) -> cb.conjunction();
        }
        
        // Check if entity should be skipped
        if (shouldSkipSecurity(entityClass)) {
            logger.debug("Skipping security filtering for entity: {}", entityClass.getSimpleName());
            return (root, query, cb) -> cb.conjunction();
        }
        
        return (Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
            String userIdField = getUserIdFieldName(entityClass);
            if (userIdField == null) {
                // No user ID field found, return no restriction
                return cb.conjunction();
            }
            
            String currentUserId = getCurrentUserId();
            if (currentUserId == null) {
                if (properties.getSecurity().isFailFast()) {
                    throw new SecurityException("No current user ID found for security filtering");
                } else {
                    logger.warn("No current user ID found for security filtering on entity: {}", entityClass.getSimpleName());
                    return cb.conjunction();
                }
            }
            
            // Create equality predicate for user ID field
            return cb.equal(root.get(userIdField), currentUserId);
        };
    }
    
    /**
     * Enhances existing filters with user ID filtering.
     * 
     * @param <T> the entity type
     * @param existingSpec the existing specification
     * @param entityClass the entity class
     * @return a new specification that includes user ID filtering
     */
    public <T> Specification<T> withUserIdFilter(Specification<T> existingSpec, Class<T> entityClass) {
        Specification<T> userIdSpec = createUserIdSpecification(entityClass);
        return existingSpec != null ? existingSpec.and(userIdSpec) : userIdSpec;
    }
    
    /**
     * Gets the user ID field name for the given entity class.
     * Uses @UserIdField annotation if present, defaults to "userId".
     * 
     * @param entityClass the entity class
     * @return the user ID field name, or null if the field doesn't exist
     */
    public String getUserIdFieldName(Class<?> entityClass) {
        return userIdFieldCache.computeIfAbsent(entityClass, clazz -> {
            // Check for @UserIdField annotation
            UserIdField annotation = clazz.getAnnotation(UserIdField.class);
            String fieldName = annotation != null ? annotation.value() : properties.getSecurity().getDefaultUserIdField();
            
            // Verify the field exists in the entity
            if (hasField(clazz, fieldName)) {
                logger.debug("Found user ID field '{}' in entity {}", fieldName, clazz.getSimpleName());
                return fieldName;
            }
            
            logger.debug("No user ID field '{}' found in entity {}", fieldName, clazz.getSimpleName());
            return null;
        });
    }
    
    /**
     * Checks if security should be skipped for the given entity class.
     * 
     * @param entityClass the entity class to check
     * @return true if security should be skipped, false otherwise
     */
    private boolean shouldSkipSecurity(Class<?> entityClass) {
        String[] skipEntities = properties.getSecurity().getSkipEntities();
        String entityClassName = entityClass.getName();
        
        return Arrays.stream(skipEntities)
                .anyMatch(skipClass -> skipClass.equals(entityClassName));
    }
    
    /**
     * Checks if the entity has the specified field.
     * 
     * @param clazz the entity class
     * @param fieldName the field name to check
     * @return true if the field exists, false otherwise
     */
    private boolean hasField(Class<?> clazz, String fieldName) {
        Class<?> currentClass = clazz;
        while (currentClass != null && currentClass != Object.class) {
            try {
                Field field = currentClass.getDeclaredField(fieldName);
                return field != null;
            } catch (NoSuchFieldException e) {
                currentClass = currentClass.getSuperclass();
            }
        }
        return false;
    }
    
    /**
     * Gets the current user ID from the security context.
     * 
     * @return the current user ID, or null if not available
     */
    private String getCurrentUserId() {
        try {
            return Context.getCurrentUserId();
        } catch (Exception e) {
            logger.debug("Unable to get current user ID: {}", e.getMessage());
            return null;
        }
    }
}
