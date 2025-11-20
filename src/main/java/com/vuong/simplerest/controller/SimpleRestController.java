package com.vuong.simplerest.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.vuong.simplerest.core.domain.repository.GenericRepository;
import com.vuong.simplerest.core.service.SimpleRestService;
import com.vuong.simplerest.util.InputSanitizer;
import com.vuong.simplerest.util.RequestValidator;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/{entity}")
@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
public class SimpleRestController<T, ID> {

    private final SimpleRestService simpleRestService;
    private final RequestValidator requestValidator;
    private final InputSanitizer inputSanitizer;

    public SimpleRestController(
            SimpleRestService simpleRestService,
            RequestValidator requestValidator,
            InputSanitizer inputSanitizer) {
        this.simpleRestService = simpleRestService;
        this.requestValidator = requestValidator;
        this.inputSanitizer = inputSanitizer;
    }

    @GetMapping
    @SuppressWarnings("unchecked")
    public <D> ResponseEntity<Page<D>> getAll(
            @PathVariable("entity") String entity,
            @RequestParam(required = false) String projection,
            @RequestParam(required = false) Map<String, String> requestParams,
            Pageable pageable
    ) {
        List<String> validationErrors = requestValidator.validatePageable(pageable);

        // Remove pageable params from filters
        Map<String, String> filters = new java.util.HashMap<>(requestParams);
        filters.remove("projection");
        filters.remove("page");
        filters.remove("size");
        filters.remove("sort");

        // Sanitize filters
        filters = inputSanitizer.sanitizeFilters(filters);

        if (!validationErrors.isEmpty()) {
            throw new IllegalArgumentException(String.join(", ", validationErrors));
        }

        GenericRepository<T, ID> repository = simpleRestService.getJpaRepository(entity);
        Class<T> entityClass = repository.getEntityClass();
        Class<D> projectionClass = simpleRestService.resolveProjectionClass(projection, entityClass);

        Page<D> result;
        if (!filters.isEmpty()) {
            result = projectionClass != null
                    ? simpleRestService.findAll(repository, filters, pageable, entityClass, projectionClass)
                    : (Page<D>) simpleRestService.findAll(repository, filters, pageable, entityClass);

        } else {
            result = projectionClass != null
                    ? simpleRestService.findAll(repository, pageable, projectionClass)
                    : (Page<D>) simpleRestService.findAll(repository, pageable);
        }

        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    @SuppressWarnings("unchecked")
    public <D> ResponseEntity<D> getById(
            @PathVariable("entity") String entity,
            @PathVariable ID id,
            @RequestParam(required = false) String projection) {
        List<String> validationErrors = requestValidator.validateId(id);
        if (!validationErrors.isEmpty()) {
            throw new IllegalArgumentException(String.join(", ", validationErrors));
        }

        GenericRepository<T, ID> repository = simpleRestService.getJpaRepository(entity);
        Class<T> entityClass = repository.getEntityClass();
        Class<D> projectionClass = simpleRestService.resolveProjectionClass(projection, entityClass);

        D result = projectionClass != null
                ? simpleRestService.getById(repository, id, projectionClass)
                : (D) simpleRestService.getById(repository, id);

        return ResponseEntity.ok(result);
    }

    @PostMapping
    public <D> ResponseEntity<D> create(
            @PathVariable("entity") String entityName,
            @RequestBody Map<String, Object> createReq,
            @RequestParam(required = false) String projection) throws Exception {
        List<String> validationErrors = requestValidator.validateEntity(createReq);
        if (!validationErrors.isEmpty()) {
            throw new IllegalArgumentException(String.join(", ", validationErrors));
        }

        GenericRepository<T, ID> repository = simpleRestService.getJpaRepository(entityName);
        Class<T> entityClass = repository.getEntityClass();
        Class<D> projectionClass = simpleRestService.resolveProjectionClass(projection, entityClass);

        D result = simpleRestService.create(repository, createReq, projectionClass);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @PutMapping("/{id}")
    public <D> ResponseEntity<D> update(
            @PathVariable("entity") String entity,
            @PathVariable ID id,
            @RequestBody Map<String, Object> updateReq,
            @RequestParam(required = false) String projection) throws Exception {
        List<String> validationErrors = requestValidator.validateId(id);
        validationErrors.addAll(requestValidator.validateEntity(updateReq));
        if (!validationErrors.isEmpty()) {
            throw new IllegalArgumentException(String.join(", ", validationErrors));
        }

        GenericRepository<T, ID> repository = simpleRestService.getJpaRepository(entity);
        Class<T> entityClass = repository.getEntityClass();
        Class<D> projectionClass = simpleRestService.resolveProjectionClass(projection, entityClass);

        D result = simpleRestService.update(repository, id, updateReq, projectionClass);
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable("entity") String entity,
            @PathVariable ID id) {
        List<String> validationErrors = requestValidator.validateId(id);
        if (!validationErrors.isEmpty()) {
            throw new IllegalArgumentException(String.join(", ", validationErrors));
        }

        GenericRepository<T, ID> repository = simpleRestService.getJpaRepository(entity);
        simpleRestService.delete(repository, id);
        return ResponseEntity.noContent().build();
    }
}
