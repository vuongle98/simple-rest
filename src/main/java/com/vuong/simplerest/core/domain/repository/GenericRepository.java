package com.vuong.simplerest.core.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.NoRepositoryBean;

/**
 * Base repository interface for all entity repositories in the Simple REST module.
 * Extends JpaRepository and JpaSpecificationExecutor for CRUD and specification support.
 * @param <T> the entity type
 * @param <Id> the ID type
 */
@NoRepositoryBean
public interface GenericRepository<T, Id> extends JpaRepository<T, Id>, JpaSpecificationExecutor<T> {
    /**
     * Returns the entity class managed by this repository.
     * @return the entity class
     */
    Class<T> getEntityClass();
}
