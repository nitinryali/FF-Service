package com.ffproject.flagservice.exception;

/**
 * Raised when a tenant is requested, or a flag is created for a tenant, that
 * does not exist.
 *
 * <p>Mapped to HTTP 404 by {@link GlobalExceptionHandler}.</p>
 */
public class TenantNotFoundException extends RuntimeException {

    private static final String DEFAULT_MESSAGE = "Tenant not found";

    public TenantNotFoundException() {
        super(DEFAULT_MESSAGE);
    }

    public TenantNotFoundException(Throwable cause) {
        super(DEFAULT_MESSAGE, cause);
    }
}
