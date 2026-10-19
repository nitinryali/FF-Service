package com.ffproject.flagservice.exception;

/**
 * Raised when a flag is requested by a name, or updated or deleted by an id,
 * that does not exist in the store.
 *
 * <p>Mapped to HTTP 404 by {@link GlobalExceptionHandler} (Requirements 1.2, 1.4,
 * 1.5, 1.8).</p>
 */
public class FlagNotFoundException extends RuntimeException {

    private static final String DEFAULT_MESSAGE = "Flag not found";

    public FlagNotFoundException() {
        super(DEFAULT_MESSAGE);
    }

    public FlagNotFoundException(Throwable cause) {
        super(DEFAULT_MESSAGE, cause);
    }
}
