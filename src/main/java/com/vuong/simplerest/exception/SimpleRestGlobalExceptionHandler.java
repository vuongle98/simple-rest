package com.vuong.simplerest.exception;

import com.vuong.simplerest.dto.ErrorResponse;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;

import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global exception handler for the Simple REST module.
 * Handles EntityNotFoundException and IllegalArgumentException by returning appropriate HTTP error responses.
 */
@RestControllerAdvice
@Component
@ConditionalOnMissingBean(SimpleRestGlobalExceptionHandler.class)
public class SimpleRestGlobalExceptionHandler {

    /**
     * Handles EntityNotFoundException and returns a 404 Not Found response.
     * @param ex the EntityNotFoundException that was thrown
     * @return ResponseEntity with ErrorResponse and 404 status
     */
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleEntityNotFound(EntityNotFoundException ex) {
        ErrorResponse errorResponse = new ErrorResponse(
            HttpStatus.NOT_FOUND.value(),
            HttpStatus.NOT_FOUND.getReasonPhrase(),
            ex.getMessage(),
            null // No request URI available in this context
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    /**
     * Handles IllegalArgumentException and returns a 400 Bad Request response.
     * @param ex the IllegalArgumentException that was thrown
     * @return ResponseEntity with ErrorResponse and 400 status
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        ErrorResponse errorResponse = new ErrorResponse(
            HttpStatus.BAD_REQUEST.value(),
            HttpStatus.BAD_REQUEST.getReasonPhrase(),
            ex.getMessage(),
            null
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(UserNotFoundException ex) {
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                HttpStatus.NOT_FOUND.getReasonPhrase(),
                ex.getMessage(),
                null
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }
}
