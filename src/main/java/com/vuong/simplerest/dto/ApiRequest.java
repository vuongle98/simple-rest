package com.vuong.simplerest.dto;

import lombok.Data;
import org.springframework.data.domain.Pageable;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * DTO representing a generic API request for entity operations.
 * Contains data payload, projection name, filters, and pagination information.
 * @param <T> the type of the data payload
 */
@Data
public class ApiRequest<T> {
    /** The data payload for the API request. */
    private T data;

    /** The name of the projection to apply to the response. */
    @NotBlank(message = "Projection cannot be blank")
    @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_-]*$", message = "Projection must be a valid identifier")
    @Size(max = 50, message = "Projection name too long")
    private String projection;

    /** Map of filter key-value pairs for querying. */
    private Map<String, String> filters;
    /** Pagination and sorting information. */
    private Pageable pageable;
} 