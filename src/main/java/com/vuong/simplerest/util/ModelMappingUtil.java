package com.vuong.simplerest.util;

import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Utility class for mapping between model objects using ModelMapper.
 * Provides methods for mapping single objects, collections, and pages.
 */
public class ModelMappingUtil {
    private static final ModelMapper modelMapper = new ModelMapper();

    /**
     * Maps an entity to the specified output class.
     * @param entity the entity to map
     * @param outClass the output class
     * @param <D> the destination type
     * @param <T> the source type
     * @return the mapped object
     */
    public static <D, T> D map(T entity, Class<D> outClass) {
        return modelMapper.map(entity, outClass);
    }

    /**
     * Maps an entity to the specified output class, returning an Optional.
     * @param entity the entity to map (can be null)
     * @param outClass the output class
     * @param <D> the destination type
     * @param <T> the source type
     * @return Optional containing the mapped object, or empty if entity is null
     */
    public static <D, T> Optional<D> mapNullable(T entity, Class<D> outClass) {
        return Optional.ofNullable(modelMapper.map(entity, outClass));
    }

    /**
     * Maps a collection of entities to a list of the specified output class.
     * @param entityList the collection of entities to map
     * @param outCLass the output class
     * @param <D> the destination type
     * @param <T> the source type
     * @return list of mapped objects
     */
    public static <D, T> List<D> mapAll(Collection<T> entityList, Class<D> outCLass) {
        return entityList.stream()
                .map(entity -> map(entity, outCLass))
                .collect(Collectors.toList());
    }

    /**
     * Maps a page of entities to a page of the specified output class.
     * @param entityPage the page of entities to map
     * @param outCLass the output class
     * @param <D> the destination type
     * @param <T> the source type
     * @return page of mapped objects
     */
    public static <D, T> Page<D> mapAll(Page<T> entityPage, Class<D> outCLass) {
        return entityPage.map(entity -> map(entity, outCLass));
    }

}
