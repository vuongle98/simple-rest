package org.vuong.simplerest.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.vuong.simplerest.core.domain.repository.GenericRepository;
import org.vuong.simplerest.core.service.GenericRestService;
import org.vuong.simplerest.dto.ApiRequest;
import org.vuong.simplerest.dto.ApiResponse;
import org.vuong.simplerest.exception.DataNotFoundException;
import org.vuong.simplerest.util.RequestValidator;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/{entity}")
@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
public class GenericRestController<T, ID> {

    private final GenericRestService genericRestService;
    private final RequestValidator requestValidator;

    public GenericRestController(
            GenericRestService genericRestService,
            RequestValidator requestValidator) {
        this.genericRestService = genericRestService;
        this.requestValidator = requestValidator;
    }

    @GetMapping
    @SuppressWarnings("unchecked")
    public <D> ResponseEntity<ApiResponse<Page<D>>> getAll(
            @PathVariable("entity") String entity,
            @RequestParam(required = false) String projection,
            @RequestParam(required = false) Map<String, String> requestParams,
            Pageable pageable
    ) {
        try {
            List<String> validationErrors = requestValidator.validatePageable(pageable);

            // Remove pageable params from filters
            Map<String, String> filters = new java.util.HashMap<>(requestParams);
            filters.remove("projection");
            filters.remove("page");
            filters.remove("size");
            filters.remove("sort");


            // Optionally validate filters if your validator has such a method
            if (!filters.isEmpty()) {
                validationErrors.addAll(requestValidator.validateFilters(filters));
            }

            if (!validationErrors.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error(String.join(", ", validationErrors)));
            }

            GenericRepository<T, ID> repository = genericRestService.getJpaRepository(entity);
            Class<T> entityClass = repository.getEntityClass();
            Class<D> projectionClass = genericRestService.resolveProjectionClass(projection, entityClass);

            Page<D> result;
            if (!filters.isEmpty()) {
                result = projectionClass != null
                        ? genericRestService.findAll(repository, filters, pageable, entityClass, projectionClass)
                        : (Page<D>) genericRestService.findAll(repository, filters, pageable, entityClass);

            } else {
                result = projectionClass != null
                        ? genericRestService.findAll(repository, pageable, projectionClass)
                        : (Page<D>) genericRestService.findAll(repository, pageable);
            }

            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch data: " + e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    @SuppressWarnings("unchecked")
    public <D> ResponseEntity<ApiResponse<D>> getById(
            @PathVariable("entity") String entity,
            @PathVariable ID id,
            @RequestParam(required = false) String projection) {
        try {
            List<String> validationErrors = requestValidator.validateId(id);
            if (!validationErrors.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error(String.join(", ", validationErrors)));
            }

            GenericRepository<T, ID> repository = genericRestService.getJpaRepository(entity);
            Class<T> entityClass = repository.getEntityClass();
            Class<D> projectionClass = genericRestService.resolveProjectionClass(projection, entityClass);

            D result = projectionClass != null
                    ? genericRestService.getById(repository, id, projectionClass)
                    : (D) genericRestService.getById(repository, id);

            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (DataNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch data: " + e.getMessage()));
        }
    }

    @PostMapping
    public <D> ResponseEntity<ApiResponse<D>> create(
            @PathVariable("entity") String entityName,
            @RequestBody Map<String, Object> createReq,
            @RequestParam(required = false) String projection) {
        try {
            List<String> validationErrors = requestValidator.validateEntity(createReq);
            if (!validationErrors.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error(String.join(", ", validationErrors)));
            }

            GenericRepository<T, ID> repository = genericRestService.getJpaRepository(entityName);
            Class<T> entityClass = repository.getEntityClass();
            Class<D> projectionClass = genericRestService.resolveProjectionClass(projection, entityClass);

            D result = genericRestService.create(repository, createReq, projectionClass);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Created successfully", result));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to create entity: " + e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public <D> ResponseEntity<ApiResponse<D>> update(
            @PathVariable("entity") String entity,
            @PathVariable ID id,
            @RequestBody Map<String, Object> updateReq,
            @RequestParam(required = false) String projection) {
        try {
            List<String> validationErrors = requestValidator.validateId(id);
            validationErrors.addAll(requestValidator.validateEntity(updateReq));
            if (!validationErrors.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error(String.join(", ", validationErrors)));
            }

            GenericRepository<T, ID> repository = genericRestService.getJpaRepository(entity);
            Class<T> entityClass = repository.getEntityClass();
            Class<D> projectionClass = genericRestService.resolveProjectionClass(projection, entityClass);

            D result = genericRestService.update(repository, id, updateReq, projectionClass);
            return ResponseEntity.ok(ApiResponse.success("Updated successfully", result));
        } catch (DataNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to update entity: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable("entity") String entity,
            @PathVariable ID id) {
        try {
            List<String> validationErrors = requestValidator.validateId(id);
            if (!validationErrors.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error(String.join(", ", validationErrors)));
            }

            GenericRepository<T, ID> repository = genericRestService.getJpaRepository(entity);
            genericRestService.delete(repository, id);
            return ResponseEntity.ok(ApiResponse.success("Deleted successfully", null));
        } catch (DataNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to delete entity: " + e.getMessage()));
        }
    }

    @GetMapping("/search")
    @SuppressWarnings("unchecked")
    public <D> ResponseEntity<ApiResponse<Page<D>>> searchWithFilters(
            @PathVariable("entity") String entity,
            @RequestBody ApiRequest<T> request) {
        try {
            List<String> validationErrors = requestValidator.validateFilters(request.getFilters());
            validationErrors.addAll(requestValidator.validatePageable(request.getPageable()));
            if (!validationErrors.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error(String.join(", ", validationErrors)));
            }

            GenericRepository<T, ID> repository = genericRestService.getJpaRepository(entity);
            Class<T> entityClass = repository.getEntityClass();
            Class<D> projectionClass = genericRestService.resolveProjectionClass(request.getProjection(), entityClass);

            Page<D> result = projectionClass != null
                    ? genericRestService.findAll(repository, request.getFilters(), request.getPageable(), entityClass, projectionClass)
                    : (Page<D>) genericRestService.findAll(repository, request.getFilters(), request.getPageable(), entityClass);

            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to search data: " + e.getMessage()));
        }
    }
}
