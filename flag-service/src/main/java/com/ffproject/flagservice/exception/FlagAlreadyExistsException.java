package com.ffproject.flagservice.exception;

/**
 * Raised when a flag is created with a name that is already taken.
 *
 * <p>Triggered by a MongoDB duplicate-key violation on the unique {@code name}
 * index, and mapped to HTTP 409 by {@link GlobalExceptionHandler}
 * (Requirement 1.7).</p>
 */
public class FlagAlreadyExistsException extends RuntimeException {

    private static final String DEFAULT_MESSAGE = "Flag already exists";

    public FlagAlreadyExistsException() {
        super(DEFAULT_MESSAGE);
    }

    public FlagAlreadyExistsException(Throwable cause) {
        super(DEFAULT_MESSAGE, cause);
    }
}
