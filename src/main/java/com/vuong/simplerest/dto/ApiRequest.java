package com.vuong.simplerest.dto;

import lombok.Data;
import org.springframework.data.domain.Pageable;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;

@Data
public class ApiRequest<T> {
    private T data;

    @NotBlank(message = "Projection cannot be blank")
    @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_-]*$", message = "Projection must be a valid identifier")
    @Size(max = 50, message = "Projection name too long")
    private String projection;

    private Map<String, String> filters;
    private Pageable pageable;
} 