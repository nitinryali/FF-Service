package com.ffproject.flagservice.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Map;

/**
 * Centralised translation of exceptions into the Flag Service's JSON error
 * envelope.
 *
 * <p>Every error body is a single {@code {"error": "..."}} object. Controllers
 * never catch their own exceptions — all mapping happens here (Requirements
 * 1.7, 1.8, 1.9, 1.10).</p>
 *
 * <p>Note: the catch-all {@code Exception} handler below also intercepts
 * framework exceptions such as {@code HttpMessageNotReadableException} (malformed
 * JSON) and {@code HttpRequestMethodNotSupportedException}, turning them into 500s.
 * See the implementation notes for details.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String ERROR_KEY = "error";
    private static final String UNEXPECTED_ERROR_MESSAGE = "An unexpected error occurred";
    private static final String VALIDATION_FALLBACK_MESSAGE = "Validation failed";

    /**
     * Handles requests for a flag that does not exist.
     *
     * @param ex the raised exception
     * @return {@code 404 {"error": "Flag not found"}}
     */
    @ExceptionHandler(FlagNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleFlagNotFound(FlagNotFoundException ex) {
        return errorResponse(HttpStatus.NOT_FOUND, "Flag not found");
    }

    /**
     * Handles attempts to create a flag whose name is already taken.
     *
     * @param ex the raised exception
     * @return {@code 409 {"error": "Flag already exists"}}
     */
    @ExceptionHandler(FlagAlreadyExistsException.class)
    public ResponseEntity<Map<String, String>> handleFlagAlreadyExists(FlagAlreadyExistsException ex) {
        return errorResponse(HttpStatus.CONFLICT, "Flag already exists");
    }

    /**
     * Handles requests for a tenant that does not exist.
     *
     * @param ex the raised exception
     * @return {@code 404 {"error": "Tenant not found"}}
     */
    @ExceptionHandler(TenantNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleTenantNotFound(TenantNotFoundException ex) {
        return errorResponse(HttpStatus.NOT_FOUND, "Tenant not found");
    }

    /**
     * Handles attempts to create a tenant whose name is already taken.
     *
     * @param ex the raised exception
     * @return {@code 409 {"error": "Tenant already exists"}}
     */
    @ExceptionHandler(TenantAlreadyExistsException.class)
    public ResponseEntity<Map<String, String>> handleTenantAlreadyExists(TenantAlreadyExistsException ex) {
        return errorResponse(HttpStatus.CONFLICT, "Tenant already exists");
    }

    /**
     * Handles attempts to create a user whose username is already taken.
     *
     * @param ex the raised exception
     * @return {@code 409 {"error": "User already exists"}}
     */
    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<Map<String, String>> handleUserAlreadyExists(UserAlreadyExistsException ex) {
        return errorResponse(HttpStatus.CONFLICT, "User already exists");
    }

    /**
     * Handles failed role checks from {@code @PreAuthorize}.
     *
     * @param ex the raised exception
     * @return {@code 403 {"error": "Access denied"}}
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(AccessDeniedException ex) {
        return errorResponse(HttpStatus.FORBIDDEN, "Access denied");
    }

    /**
     * Handles persistence-layer outages.
     *
     * <p>The underlying connection error is logged with its cause so that a
     * lost MongoDB connection is diagnosable from the service logs, while the
     * caller still receives only the generic {@code "Service unavailable"}
     * message (Requirement 6.4).</p>
     *
     * @param ex the raised exception
     * @return {@code 503 {"error": "Service unavailable"}}
     */
    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<Map<String, String>> handleServiceUnavailable(ServiceUnavailableException ex) {
        log.error("Database connection error while handling request", ex);
        return errorResponse(HttpStatus.SERVICE_UNAVAILABLE, "Service unavailable");
    }

    /**
     * Handles bean-validation failures on request bodies, returning the first
     * violated constraint's message.
     *
     * @param ex the validation failure
     * @return {@code 400 {"error": "<violated rule>"}}
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationFailure(MethodArgumentNotValidException ex) {
        List<FieldError> fieldErrors = ex.getBindingResult().getFieldErrors();
        String message = fieldErrors.isEmpty()
                ? VALIDATION_FALLBACK_MESSAGE
                : fieldErrors.get(0).getDefaultMessage();

        return errorResponse(HttpStatus.BAD_REQUEST, message);
    }

    /**
     * Catch-all for anything not handled above.
     *
     * <p>The stack trace is logged but never returned to the caller.</p>
     *
     * @param ex the unexpected exception
     * @return {@code 500 {"error": "An unexpected error occurred"}}
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception ex) {
        log.error("Unexpected error while handling request", ex);
        return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, UNEXPECTED_ERROR_MESSAGE);
    }

    private static ResponseEntity<Map<String, String>> errorResponse(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of(ERROR_KEY, message));
    }
}
