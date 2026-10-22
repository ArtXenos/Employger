package com.employger.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

/**
 * Turns exceptions that escape a controller into plain-text responses with
 * an accurate status code. The pages show the response text to the user,
 * so messages must be safe to display: database details are logged, never
 * returned.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<String> handleNotFound(ResourceNotFoundException e) {
        return text(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleInvalidRequest(IllegalArgumentException e) {
        return text(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> handleConflict(IllegalStateException e) {
        return text(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<String> handleIntegrityViolation(DataIntegrityViolationException e) {
        log.warn("Rejected write that violates a database constraint: {}",
                e.getMostSpecificCause().getMessage());

        return text(HttpStatus.CONFLICT,
                "This change conflicts with existing data, for example a duplicate record.");
    }

    @ExceptionHandler({ DataAccessException.class, CannotCreateTransactionException.class })
    public ResponseEntity<String> handleDatabaseUnavailable(Exception e) {
        log.error("Database operation failed", e);

        return text(HttpStatus.SERVICE_UNAVAILABLE,
                "The database is unavailable. Please try again later or contact the administrator.");
    }

    /**
     * A request body failed its @Valid constraints. Every problem is listed,
     * so the user can fix the whole form in one go.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<String> handleValidationFailure(MethodArgumentNotValidException e) {

        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .distinct()
                .sorted()
                .collect(Collectors.joining(" "));

        return text(HttpStatus.BAD_REQUEST,
                message.isEmpty() ? "The request contains invalid values." : message);
    }

    @ExceptionHandler({ HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class })
    public ResponseEntity<String> handleMalformedRequest(Exception e) {
        return text(HttpStatus.BAD_REQUEST,
                "The request is malformed. Check the values entered and try again.");
    }

    private static ResponseEntity<String> text(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .contentType(MediaType.TEXT_PLAIN)
                .body(message);
    }
}
