package com.vuong.simplerest.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vuong.simplerest.util.Context;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.vuong.simplerest.core.domain.repository.GenericRepository;
import com.vuong.simplerest.core.domain.specification.SpecificationBuilder;
import com.vuong.simplerest.core.projection.ProjectionHandler;
import com.vuong.simplerest.core.projection.ProjectionRegistry;
import com.vuong.simplerest.core.security.UserIdSecurityFilter;
import jakarta.persistence.EntityNotFoundException;
import com.vuong.simplerest.util.ModelMappingUtil;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service class providing CRUD operations for JPA entities in the Simple REST
 * module.
 * Handles entity retrieval, creation, update, deletion, projections, and
 * relationships.
 * Supports filtering, pagination, and dynamic projections.
 * Enhanced with caching for better performance.
 */
@Service
@Transactional
public class SimpleRestService {

    private static final Logger logger = LoggerFactory.getLogger(SimpleRestService.class);
    private final ProjectionHandler projectionHandler;
    private final ProjectionRegistry projectionRegistry;
    private final ObjectMapper objectMapper;
    private final ApplicationContext applicationContext;
    private final UserIdSecurityFilter securityFilter;

    // Caches
    private final Map<String, GenericRepository<?, ?>> repositoryMap = new ConcurrentHashMap<>();
    private final Map<Class<?>, GenericRepository<?, ?>> repositoryStartClassMap = new ConcurrentHashMap<>();
    private final Map<Class<?>, List<Field>> relationshipFieldsCache = new ConcurrentHashMap<>();
    private final Map<Class<?>, Field> idFieldCache = new ConcurrentHashMap<>();
    private final Map<String, String> kebabCaseCache = new ConcurrentHashMap<>();

    /**
     * Constructs a SimpleRestService with the required dependencies.
     * 
     * @param projectionHandler  the handler for entity projections
     * @param projectionRegistry the registry for projection mappings
     * @param objectMapper       the ObjectMapper for JSON
     *                           serialization/deserialization
     * @param applicationContext the Spring application context
     */
    public SimpleRestService(
            ProjectionHandler projectionHandler,
            ProjectionRegistry projectionRegistry,
            ObjectMapper objectMapper,
            ApplicationContext applicationContext,
            UserIdSecurityFilter securityFilter) {
        this.projectionHandler = projectionHandler;
        this.projectionRegistry = projectionRegistry;
        this.objectMapper = objectMapper;
        this.applicationContext = applicationContext;
        this.securityFilter = securityFilter;
    }

    @PostConstruct
    private void initRepositoryMap() {
        Map<String, GenericRepository> beans = applicationContext.getBeansOfType(GenericRepository.class);
        for (GenericRepository repo : beans.values()) {
            Class<?> entityClass = repo.getEntityClass();
            String entityName = toKebabCase(entityClass.getSimpleName());

            repositoryMap.put(entityName, repo);
            repositoryStartClassMap.put(entityClass, repo);

            // Pre-cache ID field
            findIdField(entityClass);

            logger.info("Registered repository for entity: {} -> {}. Entity class: {}", entityName,
                    repo.getClass().getSimpleName(), entityClass.getName());
        }
    }

    /**
     * Retrieves the GenericRepository for the specified entity name.
     * 
     * @param <T>    the entity type
     * @param <ID>   the ID type
     * @param entity the entity name in kebab-case
     * @return the repository instance for the entity
     * @throws IllegalArgumentException if no repository is found for the entity
     */
    @SuppressWarnings("unchecked")
    public <T, ID> GenericRepository<T, ID> getJpaRepository(String entity) {
        GenericRepository<?, ?> repo = repositoryMap.get(entity);
        if (repo == null) {
            throw new IllegalArgumentException("No repository found for entity: " + entity);
        }
        return (GenericRepository<T, ID>) repo;
    }

    /**
     * Retrieves the GenericRepository for the specified entity class.
     */
    @SuppressWarnings("unchecked")
    public <T, ID> GenericRepository<T, ID> getJpaRepository(Class<T> entityClass) {
        GenericRepository<?, ?> repo = repositoryStartClassMap.get(entityClass);
        if (repo == null) {
            // Fallback to name lookup if not found (unexpected)
            return getJpaRepository(toKebabCase(entityClass.getSimpleName()));
        }
        return (GenericRepository<T, ID>) repo;
    }

    /**
     * Gets the entity class from the repository.
     * 
     * @param <T>        the entity type
     * @param <ID>       the ID type
     * @param repository the repository instance
     * @return the entity class
     */
    public <T, ID> Class<T> getEntityClass(GenericRepository<T, ID> repository) {
        return repository.getEntityClass();
    }

    /**
     * Finds all entities with pagination.
     * 
     * @param <T>        the entity type
     * @param <ID>       the ID type
     * @param repository the repository instance
     * @param pageable   the pagination and sorting information
     * @return a page of entities
     */
    public <T, ID> Page<T> findAll(GenericRepository<T, ID> repository, Pageable pageable) {
        Class<T> entityClass = repository.getEntityClass();
        Specification<T> userIdSpec = securityFilter.createUserIdSpecification(entityClass);
        return repository.findAll(userIdSpec, pageable);
    }

    /**
     * Finds all entities with pagination and projection.
     * 
     * @param <T>             the entity type
     * @param <ID>            the ID type
     * @param <D>             the projection type
     * @param repository      the repository instance
     * @param pageable        the pagination and sorting information
     * @param projectionClass the projection class
     * @return a page of projected entities
     */
    public <T, ID, D> Page<D> findAll(GenericRepository<T, ID> repository, Pageable pageable,
            Class<D> projectionClass) {
        Class<T> entityClass = repository.getEntityClass();
        Specification<T> userIdSpec = securityFilter.createUserIdSpecification(entityClass);
        Page<T> page = repository.findAll(userIdSpec, pageable);
        return page.map(entity -> projectWithNested(entity, projectionClass));
    }

    /**
     * Retrieves an entity by its ID.
     * 
     * @param repository the repository for the entity
     * @param id         the entity ID
     * @param <T>        the entity type
     * @param <ID>       the ID type
     * @return the entity
     * @throws EntityNotFoundException if the entity is not found
     */
    public <T, ID> T getById(GenericRepository<T, ID> repository, ID id) {
        return findById(repository, id)
                .orElseThrow(() -> new EntityNotFoundException("Not found entity with id: " + id));
    }

    /**
     * Retrieves an entity by its ID with projection.
     * 
     * @param <T>             the entity type
     * @param <ID>            the ID type
     * @param <D>             the projection type
     * @param repository      the repository for the entity
     * @param id              the entity ID
     * @param projectionClass the projection class
     * @return the projected entity
     * @throws EntityNotFoundException if the entity is not found
     */
    public <T, ID, D> D getById(GenericRepository<T, ID> repository, ID id, Class<D> projectionClass) {
        T entity = findById(repository, id)
                .orElseThrow(() -> new EntityNotFoundException("Not found entity with id: " + id));
        return projectWithNested(entity, projectionClass);
    }

    /**
     * Finds an entity by its ID.
     * 
     * @param <T>        the entity type
     * @param <ID>       the ID type
     * @param repository the repository instance
     * @param id         the entity ID
     * @return an Optional containing the entity if found, empty otherwise
     */
    public <T, ID> Optional<T> findById(GenericRepository<T, ID> repository, ID id) {
        Class<T> entityClass = repository.getEntityClass();
        Specification<T> userIdSpec = securityFilter.createUserIdSpecification(entityClass);
        Specification<T> findByIdAndUserId = userIdSpec.and((root, query, cb) -> cb.equal(root.get("id"), id));
        return repository.findOne(findByIdAndUserId);
    }

    /**
     * Saves an entity.
     * 
     * @param <T>        the entity type
     * @param <ID>       the ID type
     * @param repository the repository instance
     * @param entity     the entity to save
     * @return the saved entity
     */
    public <T, ID> T save(GenericRepository<T, ID> repository, T entity) {
        // Validate user access before saving
        validateUserAccess(entity);
        return repository.save(entity);
    }

    /**
     * Deletes an entity by its ID.
     * 
     * @param <T>        the entity type
     * @param <ID>       the ID type
     * @param repository the repository instance
     * @param id         the entity ID
     */
    public <T, ID> void delete(GenericRepository<T, ID> repository, ID id) {
        Class<T> entityClass = repository.getEntityClass();
        logger.debug("Deleting entity of type: {} with ID: {}", entityClass.getSimpleName(), id);

        // Find entity with user ID filtering to ensure user has access
        T entity = getById(repository, id);
        
        repository.delete(entity);

        logger.info("Successfully deleted entity of type: {} with ID: {}", entityClass.getSimpleName(), id);
    }

    /**
     * Projects a list of entities to a list of maps with specified fields.
     * 
     * @param <T>    the entity type
     * @param data   the list of entities
     * @param fields the list of field names to include
     * @return a list of maps containing the projected data
     */
    public <T> List<Map<String, Object>> getProjectedData(List<T> data, List<String> fields) {
        return data.stream()
                .map(entity -> projectionHandler.project(entity, fields))
                .collect(Collectors.toList());
    }

    /**
     * Finds all entities with filters, pagination, and sorting.
     * 
     * @param <T>         the entity type
     * @param repository  the specification executor repository
     * @param filters     the filter map
     * @param pageable    the pagination and sorting information
     * @param entityClass the entity class
     * @return a page of entities matching the filters
     */
    public <T> Page<T> findAll(
            JpaSpecificationExecutor<T> repository,
            Map<String, String> filters,
            Pageable pageable,
            Class<T> entityClass) {
        Specification<T> spec = SpecificationBuilder.build(filters, entityClass);
        Specification<T> securedSpec = securityFilter.withUserIdFilter(spec, entityClass);
        return repository.findAll(securedSpec, pageable);
    }

    /**
     * Finds all entities with filters, pagination, sorting, and projection.
     * 
     * @param <T>             the entity type
     * @param <D>             the projection type
     * @param repository      the specification executor repository
     * @param filters         the filter map
     * @param pageable        the pagination and sorting information
     * @param entityClass     the entity class
     * @param projectionClass the projection class
     * @return a page of projected entities matching the filters
     */
    public <T, D> Page<D> findAll(
            JpaSpecificationExecutor<T> repository,
            Map<String, String> filters,
            Pageable pageable,
            Class<T> entityClass,
            Class<D> projectionClass) {

        Specification<T> spec = SpecificationBuilder.build(filters, entityClass);
        Specification<T> securedSpec = securityFilter.withUserIdFilter(spec, entityClass);
        Page<T> page = repository.findAll(securedSpec, pageable);
        return page.map(entity -> projectWithNested(entity, projectionClass));
    }

    /**
     * Projects an entity to a specified projection class.
     * 
     * @param <T>             the entity type
     * @param <D>             the projection type
     * @param entity          the entity to project
     * @param projectionClass the projection class
     * @return the projected entity
     */
    public <T, D> D project(T entity, Class<D> projectionClass) {
        return projectWithNested(entity, projectionClass);
    }

    /**
     * Creates a new entity from the provided data.
     * Handles relationships and projections.
     * 
     * @param repository      the repository for the entity
     * @param createReq       the creation request data as a map
     * @param projectionClass the projection class for the response, or null for
     *                        full entity
     * @param <T>             the entity type
     * @param <ID>            the ID type
     * @param <D>             the projection type
     * @return the created entity or its projection
     * @throws Exception if mapping, relationship handling, or saving fails
     */
    @SuppressWarnings("unchecked")
    public <T, ID, D> D create(GenericRepository<T, ID> repository, Map<String, Object> createReq,
            Class<D> projectionClass) throws Exception {
        Class<T> entityClass = repository.getEntityClass();
        logger.debug("Creating entity of type: {} with projection: {}", entityClass.getSimpleName(),
                projectionClass != null ? projectionClass.getSimpleName() : "none");

        T entity = ModelMappingUtil.map(createReq, entityClass);
        setEntityId(entity, null);
        handleRelationships(entity, createReq, entityClass);
        T savedEntity = save(repository, entity);

        logger.info("Successfully created entity of type: {} with ID: {}", entityClass.getSimpleName(),
                getEntityId(savedEntity));

        if (projectionClass != null) {
            try {
                Field idField = findIdField(entityClass);
                if (idField != null) {
                    idField.setAccessible(true);
                    ID entityId = (ID) idField.get(savedEntity);
                    return getById(repository, entityId, projectionClass);
                }
            } catch (Exception e) {
                // If the projection fails, return the full entity
                return (D) savedEntity;
            }
        }
        return (D) savedEntity;
    }

    private String getEntityId(Object entity) {
        if (entity == null) {
            return "null";
        }

        try {
            Field idField = findIdField(entity.getClass());
            if (idField != null) {
                idField.setAccessible(true);
                Object id = idField.get(entity);
                return id != null ? id.toString() : String.valueOf(System.identityHashCode(entity));
            }
        } catch (Exception e) {
            // Ignore and fall back to identity hash code
        }

        return String.valueOf(System.identityHashCode(entity));
    }

    private void setEntityId(Object entity, Object value) {
        if (entity == null) {
            return;
        }

        try {
            Field idField = findIdField(entity.getClass());
            if (idField != null) {
                idField.setAccessible(true);
                idField.set(entity, value);
            }
        } catch (Exception e) {
            // Ignore and fall back to identity hash code
        }
    }

    /**
     * Updates an existing entity with the provided data.
     * Handles relationships and projections.
     * 
     * @param <T>             the entity type
     * @param <ID>            the ID type
     * @param <D>             the projection type
     * @param repository      the repository for the entity
     * @param id              the entity ID
     * @param updateReq       the update request data as a map
     * @param projectionClass the projection class for the response, or null for
     *                        full entity
     * @return the updated entity or its projection
     * @throws Exception if mapping, relationship handling, or saving fails
     */
    @SuppressWarnings("unchecked")
    public <T, ID, D> D update(GenericRepository<T, ID> repository, ID id, Map<String, Object> updateReq,
            Class<D> projectionClass) throws Exception {
        Class<T> entityClass = repository.getEntityClass();
        logger.debug("Updating entity of type: {} with ID: {} and projection: {}", entityClass.getSimpleName(), id,
                projectionClass != null ? projectionClass.getSimpleName() : "none");

        T existingEntity = getById(repository, id);
        objectMapper.updateValue(existingEntity, updateReq);
        handleRelationships(existingEntity, updateReq, repository.getEntityClass());
        T savedEntity = save(repository, existingEntity);

        logger.info("Successfully updated entity of type: {} with ID: {}", entityClass.getSimpleName(), id);

        if (projectionClass != null) {
            try {
                return getById(repository, id, projectionClass);
            } catch (Exception e) {
                // If the projection fails, return the full entity
                return (D) savedEntity;
            }
        }
        return (D) savedEntity;
    }

    /**
     * Resolves the projection class for the given projection name and entity class.
     * 
     * @param <T>         the entity type
     * @param <D>         the projection type
     * @param projection  the projection name or null
     * @param entityClass the entity class
     * @return the projection class, or null if not found
     */
    @SuppressWarnings("unchecked")
    public <T, D> Class<D> resolveProjectionClass(String projection, Class<T> entityClass) {
        if (projection != null && projectionRegistry.hasProjection(projection)) {
            return (Class<D>) projectionRegistry.getProjectionClass(projection);
        } else if (projectionRegistry.hasProjectionForEntity(entityClass)) {
            return (Class<D>) projectionRegistry.getProjectionForEntity(entityClass);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private <T, D> D projectWithNested(T entity, Class<D> projectionClass) {
        if (projectionClass == null || projectionClass == Object.class) {
            return (D) entity;
        }

        try {
            D projectedEntity = projectionHandler.project(entity, projectionClass, new HashSet<>());
            if (projectedEntity == null) {
                logger.warn("Projection returned null for entity of type {} to projection type {}",
                        entity.getClass().getName(), projectionClass.getName());
                return (D) entity;
            }

            return projectedEntity;
        } catch (Exception e) {
            logger.error("Failed to project entity of type {} to projection type {}: {}",
                    entity.getClass().getName(), projectionClass.getName(), e.getMessage(), e);
            // If the projection fails, return the original entity
            return (D) entity;
        }
    }

    private <T> void handleRelationships(T entity, Map<String, Object> data, Class<T> entityClass) throws Exception {
        List<Field> relationshipFields = getRelationshipFields(entityClass);

        for (Field field : relationshipFields) {
            field.setAccessible(true);

            String fieldName = field.getName();
            Object value = data.get(fieldName + "Ids");

            if (value == null) {
                value = data.get(fieldName + "Id");

                if (value == null) {
                    continue;
                }
            }

            Class<?> relatedEntityClass = getRelationType(field);
            // Optimization: Look up repository by class directly
            GenericRepository<?, ?> relatedRepository = getJpaRepository(relatedEntityClass);

            if (Collection.class.isAssignableFrom(field.getType())) {
                handleCollectionRelationship(entity, field, value, relatedRepository);
            } else {
                handleSingleRelationship(entity, field, value, relatedRepository);
            }
        }
    }

    // Cached access to relationship fields
    private List<Field> getRelationshipFields(Class<?> entityClass) {
        return relationshipFieldsCache.computeIfAbsent(entityClass, k -> {
            List<Field> fields = new ArrayList<>();
            for (Field field : entityClass.getDeclaredFields()) {
                if (isRelationshipField(field)) {
                    fields.add(field);
                }
            }
            return fields;
        });
    }

    private boolean isRelationshipField(Field field) {
        return field.isAnnotationPresent(OneToMany.class) ||
                field.isAnnotationPresent(ManyToOne.class) ||
                field.isAnnotationPresent(ManyToMany.class) ||
                field.isAnnotationPresent(OneToOne.class);
    }

    @SuppressWarnings("unchecked")
    private <T> void handleCollectionRelationship(T entity, Field field, Object value,
            GenericRepository<?, ?> relatedRepository) throws Exception {
        Collection<?> relatedEntities;
        if (value instanceof List<?> ids) {
            relatedEntities = ((GenericRepository<Object, Object>) relatedRepository)
                    .findAllById((Iterable<Object>) ids);
        } else {
            Object id = objectMapper.convertValue(value, field.getType()); // This might need refinement for ID type
            relatedEntities = Collections
                    .singletonList(((GenericRepository<Object, Object>) relatedRepository).findById(id).orElse(null));
        }

        // Remove nulls just in case
        relatedEntities.removeIf(Objects::isNull);

        if (field.getType().isAssignableFrom(Set.class)) {
            field.set(entity, new HashSet<>(relatedEntities));
        } else if (field.getType().isAssignableFrom(List.class)) {
            field.set(entity, new ArrayList<>(relatedEntities));
        } else {
            field.set(entity, relatedEntities);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> void handleSingleRelationship(T entity, Field field, Object value,
            GenericRepository<?, ?> relatedRepository) throws Exception {
        // ID type handling could be improved, often IDs are Long or UUID/String.
        // Assuming objectMapper handles conversion to the correct ID type if possible,
        // or we imply ID is compatible.
        // Usually, we'd want to know the ID class of the repository.
        // For now, let's trust objectMapper for simple conversions or string/long.

        Object id = value;
        // Simple heuristic for common ID types if not complex object
        if (value instanceof String || value instanceof Number) {
            // Leave as is, let repository handle or simple conversion
        }

        Object relatedEntity = ((GenericRepository<Object, Object>) relatedRepository).findById(id).orElse(null);
        field.set(entity, relatedEntity);
    }

    private Class<?> getRelationType(Field field) {
        if (Collection.class.isAssignableFrom(field.getType())) {
            ParameterizedType genericType = (ParameterizedType) field.getGenericType();
            return (Class<?>) genericType.getActualTypeArguments()[0];
        }
        return field.getType();
    }

    private Field findIdField(Class<?> entityClass) {
        return idFieldCache.computeIfAbsent(entityClass, k -> {
            Class<?> currentClass = k;
            while (currentClass != null && currentClass != Object.class) {
                for (Field field : currentClass.getDeclaredFields()) {
                    if (field.isAnnotationPresent(jakarta.persistence.Id.class) ||
                            field.isAnnotationPresent(jakarta.persistence.EmbeddedId.class)) {
                        return field;
                    }
                }
                currentClass = currentClass.getSuperclass();
            }
            return null;
        });
    }

    private String toKebabCase(String camelCase) {
        return kebabCaseCache.computeIfAbsent(camelCase, k -> k.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase());
    }
    
    /**
     * Validates that the current user has access to save/update the entity.
     * 
     * @param <T> the entity type
     * @param entity the entity to validate
     * @throws SecurityException if user doesn't have access
     */
    private <T> void validateUserAccess(T entity) {
        if (entity == null) {
            return;
        }
        
        Class<?> entityClass = entity.getClass();
        String userIdField = securityFilter.getUserIdFieldName(entityClass);
        
        if (userIdField == null) {
            // No user ID field, no restriction
            return;
        }
        
        try {
            Field field = findField(entityClass, userIdField);
            if (field != null) {
                field.setAccessible(true);
                Object entityUserId = field.get(entity);
                String currentUserId = Context.getCurrentUserId();
                
                if (entityUserId != null && !entityUserId.toString().equals(currentUserId)) {
                    throw new SecurityException("User does not have permission to access this entity");
                }
                
                // Set user ID if not present (for new entities)
                if (entityUserId == null) {
                    field.set(entity, currentUserId);
                    logger.debug("Set user ID {} on entity {}", currentUserId, entityClass.getSimpleName());
                }
            }
        } catch (IllegalAccessException e) {
            logger.warn("Unable to validate user access for entity {}: {}", entityClass.getSimpleName(), e.getMessage());
        } catch (Exception e) {
            logger.error("Error validating user access: {}", e.getMessage(), e);
            throw new SecurityException("Access validation failed", e);
        }
    }
    
    /**
     * Finds a field in the class hierarchy.
     * 
     * @param clazz the class to search
     * @param fieldName the field name
     * @return the field, or null if not found
     */
    private Field findField(Class<?> clazz, String fieldName) {
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
}
