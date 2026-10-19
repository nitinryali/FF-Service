package com.ffproject.flagservice.exception;

/**
 * Raised when creating a tenant whose name is already taken.
 *
 * <p>Mapped to HTTP 409 by {@link GlobalExceptionHandler}.</p>
 */
public class TenantAlreadyExistsException extends RuntimeException {

    private static final String DEFAULT_MESSAGE = "Tenant already exists";

    public TenantAlreadyExistsException() {
        super(DEFAULT_MESSAGE);
    }

    public TenantAlreadyExistsException(Throwable cause) {
        super(DEFAULT_MESSAGE, cause);
    }
}
