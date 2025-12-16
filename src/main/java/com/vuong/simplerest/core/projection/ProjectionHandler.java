package com.vuong.simplerest.core.projection;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Component for handling entity projections to different data transfer objects or interfaces.
 * Supports projection to classes, interfaces, and collections with cycle detection and caching.
 * Used in the Simple REST module to transform JPA entities into lightweight projection objects.
 */
@Component
public class ProjectionHandler {
    private static final Logger logger = LoggerFactory.getLogger(ProjectionHandler.class);
    private final Map<ProjectionKey, Object> projectionCache = new ConcurrentHashMap<>();

    private final ObjectMapper jsonMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    // Method name constants for better maintainability
    private static final String GET_PREFIX = "get";
    private static final String SET_PREFIX = "set";
    private static final String GET_ID_METHOD = "getId";
    private static final String SET_ID_METHOD = "setId";

    /**
     * Projects an entity to a map containing only the specified fields.
     * @param entity the entity to project
     * @param fields the list of field names to include in the projection
     * @param <T> the entity type
     * @return a map with field names as keys and their values
     */
    public <T> Map<String, Object> project(T entity, List<String> fields) {
        Map<String, Object> result = new HashMap<>();

        for (String fieldName : fields) {
            try {
                Object value = getFieldValue(entity, fieldName);
                result.put(fieldName, value);
            } catch (Exception e) {
                logger.warn("Failed to project field {}: {}", fieldName, e.getMessage());
                result.put(fieldName, null);
            }
        }

        return result;
    }

    /**
     * Projects an entity to the specified projection class.
     * @param entity the entity to project
     * @param projectionClass the class or interface to project to
     * @param <T> the entity type
     * @param <D> the projection type
     * @return the projected object, or null if projection fails
     */
    public <T, D> D project(T entity, Class<D> projectionClass) {
        // Use IdentityHashMap instead of HashSet for identity-based equality
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        return project(entity, projectionClass, visited);
    }

    /**
     * Internal projection method with cycle detection support.
     * @param entity the entity to project
     * @param projectionClass the class or interface to project to
     * @param visited set of already visited entities to prevent cycles
     * @param <T> the entity type
     * @param <D> the projection type
     * @return the projected object, or null if projection fails
     */
    public <T, D> D project(T entity, Class<D> projectionClass, Set<Object> visited) {
        if (entity == null) {
            logger.warn("Cannot project null entity");
            return null;
        }

        if (projectionClass == null) {
            logger.warn("Cannot project to null projection class");
            return null;
        }

        // Get the entity ID for better cycle detection with JPA entities
        Object entityId = getEntityId(entity);

        // Create a unique key for this entity
        String entityKey = entityId != null ?
                entity.getClass().getName() + "-" + entityId :
                String.valueOf(System.identityHashCode(entity));

        // Check for cycles
        if (visited.contains(entityKey)) {
            logger.debug("Cycle detected for entity: {}", entityKey);
            return null;
        }

        // Mark this entity as visited
        visited.add(entityKey);

        try {
            // Check cache first - only use cache for root entities to avoid partial projections
            if (visited.size() == 1) {
                ProjectionKey key = new ProjectionKey(entity, projectionClass);
                @SuppressWarnings("unchecked")
                D cachedResult = (D) projectionCache.get(key);
                if (cachedResult != null && !isProjectionChanged(entity, cachedResult, projectionClass, visited)) {
                    logger.debug("Using cached projection for {}", getEntityClass(entity).getName());
                    return cachedResult;
                }
            }

            logger.debug("Starting projection from {} to {}", getEntityClass(entity).getName(), projectionClass.getName());

            D result;
            if (projectionClass.isInterface()) {
                result = createInterfaceProxy(entity, projectionClass, visited);
            } else {
                result = createClassInstance(entity, projectionClass, visited);
            }

            if (result != null && visited.size() == 1) {
                // Only cache top-level projections
                projectionCache.put(new ProjectionKey(entity, projectionClass), result);
                return result;
            }

            if (result == null) {
                logger.warn("Failed to create projection for entity of type {} to projection type {}",
                        getEntityClass(entity).getName(), projectionClass.getName());
            }

            return result;
        } catch (Exception e) {
            logger.error("Error projecting entity of type {} to projection type {}: {}",
                    getEntityClass(entity).getName(), projectionClass.getName(), e.getMessage(), e);
            throw new RuntimeException("Error projecting entity to " + projectionClass.getName(), e);
        } finally {
            // Remove from visited when done
            visited.remove(entityKey);
        }
    }

    @SuppressWarnings("unchecked")
    private <T, D> D createInterfaceProxy(T entity, Class<D> projectionClass, Set<Object> visited) {

        if (visited.contains(entity)) {
            return null;
        }
        visited.add(entity);

        Map<String, Object> values = collectInterfaceMethodValues(entity, projectionClass, visited);
        boolean hasValues = !values.isEmpty();

        if (!hasValues) {
            logger.warn("No values found for entity of type {} when creating proxy for {}",
                    entity.getClass().getName(), projectionClass.getName());
            return null;
        }

        InvocationHandler handler = createInvocationHandler(values, projectionClass);

        try {
            ClassLoader classLoader = projectionClass.getClassLoader();
            if (classLoader == null) {
                classLoader = entity.getClass().getClassLoader();
            }

            if (classLoader == null) {
                logger.error("No class loader available for proxy creation");
                return null;
            }

            return (D) Proxy.newProxyInstance(classLoader, new Class<?>[]{projectionClass}, handler);
        } catch (Exception e) {
            logger.error("Failed to create proxy for {}: {}", projectionClass.getName(), e.getMessage(), e);
            return null;
        } finally {
            // Remove from visited set so this entity can be projected in other contexts
            visited.remove(entity);
        }
    }

    /**
     * Collects method values for interface proxy creation.
     * @param entity the entity to collect values from
     * @param projectionClass the interface class
     * @param visited set of already visited entities
     * @return map of method names to their projected values
     */
    private <T> Map<String, Object> collectInterfaceMethodValues(T entity, Class<?> projectionClass, Set<Object> visited) {
        Map<String, Object> values = new HashMap<>();

        for (Method method : projectionClass.getMethods()) {
            String methodName = method.getName();
            if (!methodName.startsWith(GET_PREFIX) || methodName.length() <= 3) {
                continue;
            }

            // Safely convert method name to field name
            String fieldName = methodName.substring(3);
            fieldName = fieldName.substring(0, 1).toLowerCase() + fieldName.substring(1);

            Object value = getFieldValue(entity, fieldName);
            if (value != null) {
                Class<?> returnType = method.getReturnType();

                // Handle collection types
                if (Collection.class.isAssignableFrom(returnType)) {
                    value = projectCollectionForInterface(value, returnType, method, visited);
                } else if (isProjectionInterface(returnType)) {
                    // Handle nested projection interfaces
                    value = project(value, returnType, visited);
                } else {
                    value = projectValue(value, returnType, visited);
                }

                values.put(methodName, value);
                logger.debug("Stored projected value for {}: {}", methodName, value);
            }
        }

        return values;
    }

    /**
     * Projects a collection for interface method return types.
     */
    private Object projectCollectionForInterface(Object value, Class<?> returnType, Method method, Set<Object> visited) {
        if (!(value instanceof Collection<?> collection)) {
            return value;
        }

        Collection<Object> projectedCollection = createCollectionInstance(returnType);

        // Get the element type from the method's generic return type
        Type genericReturnType = method.getGenericReturnType();
        if (genericReturnType instanceof ParameterizedType paramType) {
            Type[] typeArguments = paramType.getActualTypeArguments();
            if (typeArguments.length > 0) {
                Type elementType = typeArguments[0];
                if (elementType instanceof Class<?> elementClass) {
                    for (Object element : collection) {
                        if (element != null) {
                            Object projectedElement;
                            // Handle nested projection interfaces in collections
                            if (isProjectionInterface(elementClass)) {
                                projectedElement = project(element, elementClass, visited);
                            } else {
                                projectedElement = project(element, elementClass, visited);
                            }
                            if (projectedElement != null) {
                                projectedCollection.add(projectedElement);
                            }
                        }
                    }
                }
            }
        }
        return projectedCollection;
    }

    /**
     * Creates an appropriate collection instance based on the return type.
     */
    private Collection<Object> createCollectionInstance(Class<?> returnType) {
        if (returnType.isAssignableFrom(Set.class)) {
            return new HashSet<>();
        } else if (returnType.isAssignableFrom(List.class)) {
            return new ArrayList<>();
        } else {
            return new ArrayList<>();
        }
    }

    /**
     * Creates the invocation handler for interface proxies.
     */
    private InvocationHandler createInvocationHandler(Map<String, Object> values, Class<?> projectionClass) {
        return (proxy, method, args) -> {
            String methodName = method.getName();
            if (methodName.startsWith(GET_PREFIX)) {
                return values.get(methodName);
            } else if (methodName.startsWith(SET_PREFIX)) {
                // Store the value for the corresponding getter
                String getterName = GET_PREFIX + methodName.substring(3);
                values.put(getterName, args[0]);
                return null;
            }
            return switch (methodName) {
                case "toString" -> "Proxy for " + projectionClass.getName();
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> null;
            };
        };
    }

    @SuppressWarnings("unchecked")
    private <T, D> D createClassInstance(T entity, Class<D> projectionClass, Set<Object> visited) {
        // Guard against null inputs
        if (entity == null) {
            return null;
        }

        // Protect against cycles - entity already being processed
        if (visited.contains(entity)) {
            logger.debug("Cycle detected, skipping projection of {}", entity.getClass().getName());
            return null;
        }

        // Record that we're processing this entity
        visited.add(entity);

        try {
            // Handle primitive types and their wrappers
            if (isPrimitiveOrWrapper(projectionClass)) {
                Object value = getFieldValue(entity, projectionClass.getSimpleName().toLowerCase());
                return (D) convertToType(value, projectionClass);
            }

            // Try to create instance using constructor
            D instance = createInstanceFromConstructor(entity, projectionClass, visited);
            if (instance != null) {
                populateInstance(instance, entity, visited);
            }
            return instance;
        } catch (Exception e) {
            logger.error("Failed to create instance for {}: {}", projectionClass.getName(), e.getMessage());
            return null;
        } finally {
            // Remove from visited set so this entity can be projected in other contexts
            visited.remove(entity);
        }
    }

    /**
     * Creates an instance using constructor matching or no-args constructor.
     */
    private <T, D> D createInstanceFromConstructor(T entity, Class<D> projectionClass, Set<Object> visited) throws Exception {
        // Try to create instance with no-args constructor first
        try {
            D instance = projectionClass.getDeclaredConstructor().newInstance();
            populateInstanceFromConstructorArgs(instance, entity, projectionClass, visited);
            return instance;
        } catch (Exception e) {
            // If no-args constructor fails, try to find a constructor with matching parameters
            return createInstanceFromParameterizedConstructor(entity, projectionClass, visited);
        }
    }

    /**
     * Attempts to create instance using a parameterized constructor.
     */
    private <T, D> D createInstanceFromParameterizedConstructor(T entity, Class<D> projectionClass, Set<Object> visited) throws Exception {
        Constructor<?>[] constructors = projectionClass.getDeclaredConstructors();
        for (Constructor<?> constructor : constructors) {
            try {
                Class<?>[] paramTypes = constructor.getParameterTypes();
                Object[] args = createConstructorArgs(entity, constructor, paramTypes);

                @SuppressWarnings("unchecked")
                D instance = (D) constructor.newInstance(args);
                populateInstanceFromConstructorArgs(instance, entity, projectionClass, visited);
                return instance;
            } catch (Exception ex) {
                // Try next constructor
                continue;
            }
        }
        throw new RuntimeException("No suitable constructor found for " + projectionClass.getName());
    }

    /**
     * Creates constructor arguments by matching parameter names to entity fields.
     */
    private <T> Object[] createConstructorArgs(T entity, Constructor<?> constructor, Class<?>[] paramTypes) throws Exception {
        Object[] args = new Object[paramTypes.length];

        // Try to get values for constructor parameters
        for (int i = 0; i < paramTypes.length; i++) {
            String paramName = getParameterName(constructor, i);
            Object value = getFieldValue(entity, paramName);
            if (value != null) {
                args[i] = convertToType(value, paramTypes[i]);
            }
        }

        return args;
    }

    /**
     * Populates instance with additional properties after constructor initialization.
     */
    private <T, D> void populateInstanceFromConstructorArgs(D instance, T entity, Class<D> projectionClass, Set<Object> visited) {
        // Additional population logic can be added here if needed
        // For now, this is handled by the main populateInstance method
    }

    private <T, D> void populateInstance(D instance, T entity, Set<Object> visited) {
        for (Method method : instance.getClass().getMethods()) {
            String methodName = method.getName();
            if (!methodName.startsWith(SET_PREFIX) || methodName.length() <= 3) {
                continue;
            }

            // Safely convert method name to field name
            String fieldName = methodName.substring(3);
            fieldName = fieldName.substring(0, 1).toLowerCase() + fieldName.substring(1);

            Object value = getFieldValue(entity, fieldName);
            if (value != null) {
                try {
                    value = projectValue(value, method.getParameterTypes()[0], visited);
                    method.invoke(instance, value);
                } catch (Exception e) {
                    logger.warn("Failed to set value for field {}: {}", fieldName, e.getMessage());
                }
            }
        }
    }

    private String getParameterName(Constructor<?> constructor, int index) {
        try {
            // Try to get parameter name from annotations
            Parameter[] parameters = constructor.getParameters();
            if (parameters.length > index) {
                return parameters[index].getName();
            }
        } catch (Exception e) {
            // Ignore if parameter names are not available
        }

        // Fallback to a default name based on index
        return "arg" + index;
    }

    /**
     * Unwraps Hibernate proxy to get the actual entity class.
     * This handles cases where JPA lazy-loading returns proxy objects.
     */
    private Object unwrapHibernateProxy(Object entity) {
        if (entity == null) {
            return null;
        }
        
        Class<?> entityClass = entity.getClass();
        String className = entityClass.getName();
        
        // Check if this is a Hibernate proxy
        if (className.contains("$HibernateProxy") || 
            className.contains("HibernateProxy") ||
            entityClass.getName().contains("_$$_javassist") ||
            entityClass.getName().contains("_$$_jvst")) {
            
            try {
                // Try to get the actual entity using Hibernate's getLazyInitializer
                Object lazyInitializer = entityClass.getMethod("getHibernateLazyInitializer").invoke(entity);
                if (lazyInitializer != null) {
                    Object persistentClass = lazyInitializer.getClass().getMethod("getPersistentClass").invoke(lazyInitializer);
                    if (persistentClass instanceof Class) {
                        logger.debug("Unwrapped Hibernate proxy: {} -> {}", className, ((Class<?>) persistentClass).getName());
                    }
                }
            } catch (Exception e) {
                logger.debug("Could not unwrap Hibernate proxy, using as-is: {}", className);
            }
        }
        
        return entity;
    }

    /**
     * Gets the actual entity class, unwrapping proxies if necessary.
     */
    private Class<?> getEntityClass(Object entity) {
        if (entity == null) {
            return null;
        }
        
        Class<?> entityClass = entity.getClass();
        String className = entityClass.getName();
        
        // Check if this is a Hibernate proxy
        if (className.contains("$HibernateProxy") || 
            className.contains("HibernateProxy") ||
            entityClass.getName().contains("_$$_javassist") ||
            entityClass.getName().contains("_$$_jvst")) {
            
            try {
                // Try to get the actual entity class using Hibernate's getLazyInitializer
                Object lazyInitializer = entityClass.getMethod("getHibernateLazyInitializer").invoke(entity);
                if (lazyInitializer != null) {
                    Object persistentClass = lazyInitializer.getClass().getMethod("getPersistentClass").invoke(lazyInitializer);
                    if (persistentClass instanceof Class) {
                        Class<?> actualClass = (Class<?>) persistentClass;
                        logger.debug("Unwrapped Hibernate proxy class: {} -> {}", className, actualClass.getName());
                        return actualClass;
                    }
                }
            } catch (Exception e) {
                logger.debug("Could not unwrap Hibernate proxy class, using as-is: {}", className);
            }
        }
        
        return entityClass;
    }

    private boolean isProjectionInterface(Class<?> clazz) {
        return clazz.isInterface() && 
               (clazz.isAnnotationPresent(ProjectionDefinition.class) ||
                hasGetterMethods(clazz));
    }

    private boolean hasGetterMethods(Class<?> clazz) {
        for (Method method : clazz.getMethods()) {
            if (method.getName().startsWith(GET_PREFIX) && 
                method.getParameterCount() == 0 && 
                !method.getName().equals("getClass")) {
                return true;
            }
        }
        return false;
    }

    private Object projectValue(Object value, Class<?> targetType, Set<Object> visited) {
        if (value == null) {
            return null;
        }

        // First handle primitive types immediately - they can't have cycles
        if (isPrimitiveOrWrapper(targetType)) {
            return convertToType(value, targetType);
        }

        // Handle nested projection interfaces
        if (isProjectionInterface(targetType)) {
            return project(value, targetType, visited);
        }

        // Handle complex object projection with cycle detection
        return projectComplexValue(value, targetType, visited);
    }

    /**
     * Projects complex objects (non-primitives) with cycle detection.
     */
    private Object projectComplexValue(Object value, Class<?> targetType, Set<Object> visited) {
        // Get the entity identifier if possible (for JPA entities)
        Object entityId = getEntityId(value);

        // For entities, we'll use a combination of class and ID for better cycle detection
        String entityKey = entityId != null ?
                value.getClass().getName() + "-" + entityId :
                String.valueOf(System.identityHashCode(value));

        // Check if we're already processing this entity
        if (visited.contains(entityKey)) {
            logger.debug("Detected cycle in object graph for {}: {}", value.getClass().getName(), entityKey);
            return handleCyclicReference(value, targetType, entityId);
        }

        // Add to visited before processing
        visited.add(entityKey);

        try {
            return performProjection(value, targetType, visited);
        } finally {
            // Remove from visited set after processing
            visited.remove(entityKey);
        }
    }

    /**
     * Handles cyclic references by creating minimal references or returning null.
     */
    private Object handleCyclicReference(Object value, Class<?> targetType, Object entityId) {
        // For many-to-one and one-to-one relationships, return minimal ID reference
        if (entityId != null && !Collection.class.isAssignableFrom(value.getClass())) {
            return createMinimalEntityReference(value, targetType, entityId);
        }
        return null;
    }

    /**
     * Performs the actual projection based on the target type.
     */
    private Object performProjection(Object value, Class<?> targetType, Set<Object> visited) {
        // Handle collections
        if (value instanceof Collection<?> || Collection.class.isAssignableFrom(targetType)) {
            return projectCollection((Collection<?>) value, targetType, visited);
        }
        // Handle interface projections
        else if (isProjectionInterface(targetType)) {
            return project(value, targetType, visited);
        }
        // Handle other types
        else {
            return projectToConcreteClass(value, targetType, visited);
        }
    }

    /**
     * Projects value to a concrete class, with fallback mechanisms.
     */
    private Object projectToConcreteClass(Object value, Class<?> targetType, Set<Object> visited) {
        try {
            return project(value, targetType, visited);
        } catch (Exception e) {
            try {
                return targetType.cast(value);
            } catch (ClassCastException ex) {
                return createInstanceWithPropertyCopy(value, targetType, visited);
            }
        }
    }

    /**
     * Creates a new instance and copies properties when direct projection fails.
     */
    private Object createInstanceWithPropertyCopy(Object value, Class<?> targetType, Set<Object> visited) {
        try {
            Object newInstance = targetType.getDeclaredConstructor().newInstance();
            copyProperties(value, newInstance, visited);
            return newInstance;
        } catch (Exception exc) {
            logger.warn("Failed to handle value: {}", exc.getMessage());
            return null;
        }
    }

    /**
     * Gets the entity ID if the object is a JPA entity
     */
    private Object getEntityId(Object entity) {
        if (entity == null) {
            return null;
        }

        Class<?> actualClass = getEntityClass(entity);
        try {
            // Try to get ID from getId() method commonly found in JPA entities
            Method idGetter = findMethodInClassHierarchy(actualClass, GET_ID_METHOD);
            if (idGetter != null) {
                idGetter.setAccessible(true);
                return idGetter.invoke(entity);
            }

            // Try to find a field with @Id annotation
            for (Field field : actualClass.getDeclaredFields()) {
                if (field.isAnnotationPresent(jakarta.persistence.Id.class)) {
                    field.setAccessible(true);
                    return field.get(entity);
                }
            }
        } catch (Exception e) {
            logger.trace("Failed to get entity ID: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Creates a minimal reference to an entity with just its ID for cyclic references
     */
    private Object createMinimalEntityReference(Object entity, Class<?> targetType, Object entityId) {
        try {
            // For interfaces, create a minimal proxy with just the ID
            if (targetType.isInterface()) {
                InvocationHandler handler = (proxy, method, args) -> {
                    String methodName = method.getName();
                    if (methodName.equals(GET_ID_METHOD)) {
                        return entityId;
                    } else if (methodName.equals("toString")) {
                        return "Reference to " + entity.getClass().getSimpleName() + " with ID " + entityId;
                    } else if (methodName.equals("hashCode")) {
                        return entityId.hashCode();
                    } else if (methodName.equals("equals")) {
                        return proxy == args[0];
                    }
                    // Return null or default value for all other methods
                    return null;
                };

                return Proxy.newProxyInstance(
                        targetType.getClassLoader(),
                        new Class<?>[]{targetType},
                        handler
                );
            }
            // For concrete classes, try creating instance and setting ID only
            else {
                try {
                    Object instance = targetType.getDeclaredConstructor().newInstance();
                    Method setId = findMethodInClassHierarchy(targetType, SET_ID_METHOD);
                    if (setId != null) {
                        setId.invoke(instance, entityId);
                    }
                    return instance;
                } catch (Exception e) {
                    logger.trace("Could not create minimal reference: {}", e.getMessage());
                    return null;
                }
            }
        } catch (Exception e) {
            logger.trace("Failed to create minimal entity reference: {}", e.getMessage());
            return null;
        }
    }

    private Object convertToType(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }

        try {
            // Handle primitive types and their wrappers
            if (targetType.isPrimitive()) {
                if (targetType == boolean.class) {
                    return Boolean.parseBoolean(value.toString());
                } else if (targetType == byte.class) {
                    return Byte.parseByte(value.toString());
                } else if (targetType == char.class) {
                    return value.toString().charAt(0);
                } else if (targetType == double.class) {
                    return Double.parseDouble(value.toString());
                } else if (targetType == float.class) {
                    return Float.parseFloat(value.toString());
                } else if (targetType == int.class) {
                    return Integer.parseInt(value.toString());
                } else if (targetType == long.class) {
                    return Long.parseLong(value.toString());
                } else if (targetType == short.class) {
                    return Short.parseShort(value.toString());
                }
            } else {
                // Handle wrapper types and special types
                if (targetType == Boolean.class) {
                    return Boolean.valueOf(value.toString());
                } else if (targetType == Byte.class) {
                    return Byte.valueOf(value.toString());
                } else if (targetType == Character.class) {
                    return value.toString().charAt(0);
                } else if (targetType == Double.class) {
                    return Double.valueOf(value.toString());
                } else if (targetType == Float.class) {
                    return Float.valueOf(value.toString());
                } else if (targetType == Integer.class) {
                    return Integer.valueOf(value.toString());
                } else if (targetType == Long.class) {
                    return Long.valueOf(value.toString());
                } else if (targetType == Short.class) {
                    return Short.valueOf(value.toString());
                } else if (targetType == String.class) {
                    return value.toString();
                } else if (targetType == Instant.class) {
                    if (value instanceof Instant) {
                        return value;
                    }
                    return Instant.parse(value.toString());
                } else if (targetType == LocalDate.class) {
                    if (value instanceof LocalDate) {
                        return value;
                    }
                    return LocalDate.parse(value.toString());
                } else if (targetType == LocalTime.class) {
                    if (value instanceof LocalTime) {
                        return value;
                    }
                    return LocalTime.parse(value.toString());
                }
            }
            return value;
        } catch (Exception e) {
            logger.warn("Failed to convert value {} to type {}: {}", value, targetType.getName(), e.getMessage());
            return null;
        }
    }

    private boolean isPrimitiveOrWrapper(Class<?> type) {
        return type.isPrimitive() ||
                type.isEnum() ||
                type == Boolean.class ||
                type == Byte.class ||
                type == Character.class ||
                type == Double.class ||
                type == Float.class ||
                type == Integer.class ||
                type == Long.class ||
                type == Short.class ||
                type == String.class ||
                type == LocalDate.class ||
                type == LocalTime.class ||
                type == Date.class ||
                type == Instant.class;
    }

    private Class<?> getCollectionElementType(Class<?> collectionType) {
        try {
            if (collectionType.isArray()) {
                return collectionType.getComponentType();
            }

            if (Collection.class.isAssignableFrom(collectionType)) {
                // First try to get the type from the generic superclass
                Type genericType = collectionType.getGenericSuperclass();
                if (genericType instanceof ParameterizedType) {
                    Type[] typeArguments = ((ParameterizedType) genericType).getActualTypeArguments();
                    if (typeArguments.length > 0) {
                        Type elementType = typeArguments[0];
                        if (elementType instanceof Class) {
                            return (Class<?>) elementType;
                        } else if (elementType instanceof ParameterizedType nestedType) {
                            // Handle nested generic types
                            if (nestedType.getRawType() instanceof Class) {
                                return (Class<?>) nestedType.getRawType();
                            }
                        }
                    }
                }

                // If no generic type found, try to get it from the interface
                for (Type interfaceType : collectionType.getGenericInterfaces()) {
                    if (interfaceType instanceof ParameterizedType paramType) {
                        if (paramType.getRawType() == Collection.class) {
                            Type[] typeArguments = paramType.getActualTypeArguments();
                            if (typeArguments.length > 0) {
                                Type elementType = typeArguments[0];
                                if (elementType instanceof Class) {
                                    return (Class<?>) elementType;
                                } else if (elementType instanceof ParameterizedType nestedType) {
                                    if (nestedType.getRawType() instanceof Class) {
                                        return (Class<?>) nestedType.getRawType();
                                    }
                                }
                            }
                        }
                    }
                }

                // If still no type found, try to get it from the iterator
                Method iteratorMethod = collectionType.getMethod("iterator");
                Class<?> iteratorType = iteratorMethod.getReturnType();
                Method nextMethod = iteratorType.getMethod("next");
                return nextMethod.getReturnType();
            }

            return Object.class;
        } catch (Exception e) {
            logger.warn("Failed to get collection element type: {}", e.getMessage());
            return Object.class;
        }
    }

    private <T> Object projectCollection(Collection<T> collection, Class<?> targetType, Set<Object> visited) {
        if (collection == null) {
            return null;
        }

        // Handle empty collections quickly
        if (collection.isEmpty()) {
            return createEmptyCollection(targetType);
        }

        // Convert Hibernate collections to standard Java collections to avoid LazyInitializationException
        Collection<T> standardCollection = convertToStandardCollection(collection);

        Class<?> elementType = getCollectionElementType(targetType);
        if (elementType == null || elementType == Object.class) {
            // Create the appropriate collection type based on target type without projection
            return createUnprojectedCollection(standardCollection, targetType);
        }

        // For primitive element types, do a simple conversion
        if (isPrimitiveOrWrapper(elementType)) {
            return projectPrimitiveElementCollection(standardCollection, targetType, elementType);
        }

        // Project complex element types
        return projectComplexElementCollection(standardCollection, targetType, elementType, visited);
    }

    /**
     * Creates an empty collection of the appropriate type.
     */
    private Object createEmptyCollection(Class<?> targetType) {
        if (targetType.isAssignableFrom(Set.class)) {
            return new HashSet<>();
        } else {
            return new ArrayList<>();
        }
    }

    /**
     * Converts Hibernate collections to standard Java collections.
     */
    private <T> Collection<T> convertToStandardCollection(Collection<T> collection) {
        return collection.getClass().getName().contains("org.hibernate.collection")
                ? new ArrayList<>(collection)
                : collection;
    }

    /**
     * Creates a collection without projecting elements (for Object element types).
     */
    private <T> Object createUnprojectedCollection(Collection<T> collection, Class<?> targetType) {
        if (targetType.isAssignableFrom(Set.class)) {
            return new HashSet<>(collection);
        } else if (targetType.isAssignableFrom(List.class)) {
            return new ArrayList<>(collection);
        } else {
            return collection;
        }
    }

    /**
     * Projects collections with primitive element types.
     */
    private <T> Object projectPrimitiveElementCollection(Collection<T> collection, Class<?> targetType, Class<?> elementType) {
        Collection<Object> resultCollection = createCollectionForType(targetType);

        for (T item : collection) {
            if (item != null) {
                resultCollection.add(convertToType(item, elementType));
            }
        }

        return resultCollection;
    }

    /**
     * Projects collections with complex element types.
     */
    private <T> Object projectComplexElementCollection(Collection<T> collection, Class<?> targetType, Class<?> elementType, Set<Object> visited) {
        try {
            Collection<Object> resultCollection = createCollectionForType(targetType);
            Set<String> processedKeys = new HashSet<>();

            for (T item : collection) {
                if (item != null) {
                    Object projectedItem = projectCollectionItem(item, elementType, processedKeys, visited);
                    if (projectedItem != null) {
                        resultCollection.add(projectedItem);
                    }
                }
            }

            return resultCollection;
        } catch (Exception e) {
            logger.error("Failed to project collection: {}", e.getMessage(), e);
            return createFallbackCollection(collection, targetType);
        }
    }

    /**
     * Creates a collection instance based on the target type.
     */
    private Collection<Object> createCollectionForType(Class<?> targetType) {
        if (targetType.isAssignableFrom(Set.class)) {
            return new HashSet<>();
        } else if (targetType.isAssignableFrom(List.class)) {
            return new ArrayList<>();
        } else {
            return new ArrayList<>();
        }
    }

    /**
     * Projects a single collection item with cycle detection.
     */
    private <T> Object projectCollectionItem(T item, Class<?> elementType, Set<String> processedKeys, Set<Object> visited) {
        // Get the element ID if available for better cycle detection
        Object itemId = getEntityId(item);
        String itemKey = itemId != null ?
                item.getClass().getName() + "-" + itemId :
                String.valueOf(System.identityHashCode(item));

        // Skip duplicates in the same collection
        if (processedKeys.contains(itemKey)) {
            return null;
        }

        processedKeys.add(itemKey);

        // Project the item with a new visited set to prevent "horizontal" cycles
        Set<Object> itemVisited = new HashSet<>(visited);
        return projectValue(item, elementType, itemVisited);
    }

    /**
     * Creates a fallback collection when projection fails.
     */
    private <T> Object createFallbackCollection(Collection<T> collection, Class<?> targetType) {
        if (targetType.isAssignableFrom(Set.class)) {
            return new HashSet<>(collection);
        } else if (targetType.isAssignableFrom(List.class)) {
            return new ArrayList<>(collection);
        } else {
            return collection;
        }
    }

    private void copyProperties(Object source, Object target, Set<Object> visited) {
        if (source == null || target == null) {
            return;
        }

        // Skip if already visited to prevent cycles
        if (visited.contains(source)) {
            return;
        }
        // Mark as visited
        visited.add(source);

        try {
            copyPropertiesInternal(source, target, visited);
        } catch (Exception e) {
            logger.warn("Failed to copy properties: {}", e.getMessage());
        } finally {
            // Remove from visited when done
            visited.remove(source);
        }
    }

    /**
     * Internal method to copy properties using getter/setter pattern.
     */
    private void copyPropertiesInternal(Object source, Object target, Set<Object> visited) {
        for (Method getter : source.getClass().getMethods()) {
            if (isValidGetter(getter)) {
                copyProperty(getter, source, target, visited);
            }
        }
    }

    /**
     * Checks if a method is a valid getter for property copying.
     */
    private boolean isValidGetter(Method getter) {
        String methodName = getter.getName();
        return methodName.startsWith(GET_PREFIX) &&
                !methodName.equals("getClass") &&
                getter.getParameterCount() == 0;
    }

    /**
     * Copies a single property from source to target.
     */
    private void copyProperty(Method getter, Object source, Object target, Set<Object> visited) {
        String setterName = SET_PREFIX + getter.getName().substring(3);
        try {
            // Find matching setter
            Method setter = target.getClass().getMethod(setterName, getter.getReturnType());
            Object value = getter.invoke(source);

            if (value != null) {
                // Handle cyclic references in property values
                if (visited.contains(value)) {
                    return;
                }

                copyPropertyValue(value, setter, target, visited);
            }
        } catch (Exception e) {
            // Skip if setter doesn't exist or types don't match
            logger.trace("Failed to copy property {}: {}", getter.getName(), e.getMessage());
        }
    }

    /**
     * Copies a property value, handling different types appropriately.
     */
    private void copyPropertyValue(Object value, Method setter, Object target, Set<Object> visited) throws Exception {
        // For collection types, we should project each element
        if (value instanceof Collection<?>) {
            Object projectedCollection = projectCollection((Collection<?>) value, setter.getParameterTypes()[0], visited);
            setter.invoke(target, projectedCollection);
        }
        // For complex objects, we might need to project them
        else if (!isPrimitiveOrWrapper(value.getClass())) {
            Object projected = projectValue(value, setter.getParameterTypes()[0], visited);
            if (projected != null) {
                setter.invoke(target, projected);
            }
        }
        // For primitive types, copy directly
        else {
            setter.invoke(target, value);
        }
    }

    private <T> Object getFieldValue(T entity, String fieldName) {
        if (entity == null || fieldName == null || fieldName.isEmpty()) {
            return null;
        }

        try {
            // First try to get the value through a getter method
            String getterName = GET_PREFIX + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
            Method getter = findMethodInClassHierarchy(entity.getClass(), getterName);
            if (getter != null) {
                getter.setAccessible(true);
                return getter.invoke(entity);
            }

            // If no getter exists, try to get the field directly
            Field field = findFieldInClassHierarchy(entity.getClass(), fieldName);
            if (field != null) {
                field.setAccessible(true);
                return field.get(entity);
            }

            // If field is not found, try to handle it as a nested property
            if (fieldName.contains(".")) {
                String[] parts = fieldName.split("\\.");
                Object currentValue = entity;

                for (String part : parts) {
                    if (currentValue == null || part == null || part.isEmpty()) {
                        return null;
                    }
                    currentValue = getFieldValue(currentValue, part);
                }
                return currentValue;
            }

            logger.warn("Field or getter not found for {} in class {}", fieldName, entity.getClass().getName());
            return null;
        } catch (Exception e) {
            logger.warn("Failed to get value for {}: {}", fieldName, e.getMessage());
            return null;
        }
    }

    private Method findMethodInClassHierarchy(Class<?> clazz, String methodName) {
        Class<?> currentClass = clazz;
        while (currentClass != null && currentClass != Object.class) {
            try {
                return currentClass.getDeclaredMethod(methodName);
            } catch (NoSuchMethodException e) {
                currentClass = currentClass.getSuperclass();
            }
        }
        return null;
    }

    private Field findFieldInClassHierarchy(Class<?> clazz, String fieldName) {
        Class<?> currentClass = clazz;
        while (currentClass != null && currentClass != Object.class) {
            try {
                return currentClass.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                currentClass = currentClass.getSuperclass();
            }
        }
        return null;
    }

    private boolean isProjectionChanged(Object entity, Object cachedProjection, Class<?> projectionClass, Set<Object> visited) {
        try {

            // Dựng projection hiện tại từ entity
            Object currentProjection = projectionClass.isInterface()
                    ? createInterfaceProxy(entity, projectionClass, visited)
                    : createClassInstance(entity, projectionClass, visited);

            // So sánh JSON
            String json1 = jsonMapper.writeValueAsString(currentProjection);
            String json2 = jsonMapper.writeValueAsString(cachedProjection);

            return !json1.equals(json2); // nếu khác => cần cập nhật cache
        } catch (JsonProcessingException e) {
            logger.warn("Error comparing projection JSON", e);
            return true;
        }
    }

    private static class ProjectionKey {
        private final String entityId;
        private final Class<?> entityClass;
        private final Class<?> projectionClass;

        public ProjectionKey(Object entity, Class<?> projectionClass) {
            this.projectionClass = projectionClass;
            this.entityClass = entity.getClass();

            // Try to get entity ID for stable cache keys
            Object id = null;
            try {
                Method getter = getIdGetter(entity.getClass());
                if (getter != null) {
                    getter.setAccessible(true);
                    id = getter.invoke(entity);
                }
            } catch (Exception e) {
                // Ignore and fall back to identity hash code
            }

            // If we have an ID, use it; otherwise use identity hashcode
            this.entityId = id != null ?
                    entity.getClass().getName() + "-" + id :
                    entity.getClass().getName() + "-" + System.identityHashCode(entity);
        }

        private Method getIdGetter(Class<?> clazz) {
            try {
                // Try standard getId method
                return clazz.getMethod(GET_ID_METHOD);
            } catch (NoSuchMethodException e) {
                // Try to find any method or field marked with @Id
                for (Method method : clazz.getMethods()) {
                    if (method.isAnnotationPresent(jakarta.persistence.Id.class)) {
                        return method;
                    }
                }

                // If not found, check superclasses
                Class<?> superClass = clazz.getSuperclass();
                if (superClass != null && superClass != Object.class) {
                    return getIdGetter(superClass);
                }

                return null;
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ProjectionKey that = (ProjectionKey) o;
            return Objects.equals(entityId, that.entityId) &&
                    Objects.equals(entityClass, that.entityClass) &&
                    Objects.equals(projectionClass, that.projectionClass);
        }

        @Override
        public int hashCode() {
            return Objects.hash(entityId, entityClass, projectionClass);
        }
    }
}


