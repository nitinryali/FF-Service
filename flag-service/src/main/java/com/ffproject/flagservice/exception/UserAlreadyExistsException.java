package com.ffproject.flagservice.exception;

/**
 * Raised when creating a user whose username is already taken.
 *
 * <p>Mapped to HTTP 409 by {@link GlobalExceptionHandler}.</p>
 */
public class UserAlreadyExistsException extends RuntimeException {

    private static final String DEFAULT_MESSAGE = "User already exists";

    public UserAlreadyExistsException() {
        super(DEFAULT_MESSAGE);
    }

    public UserAlreadyExistsException(Throwable cause) {
        super(DEFAULT_MESSAGE, cause);
    }
}
