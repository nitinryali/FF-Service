package com.ffproject.flagservice.exception;

/**
 * Raised when a flag operation cannot be completed because the persistence layer
 * is unavailable.
 *
 * <p>Because the flag was never persisted, no Redis event is published for the
 * failed operation. Mapped to HTTP 503 by {@link GlobalExceptionHandler}
 * (Requirement 1.10).</p>
 */
public class ServiceUnavailableException extends RuntimeException {

    private static final String DEFAULT_MESSAGE = "Service unavailable";

    public ServiceUnavailableException() {
        super(DEFAULT_MESSAGE);
    }

    public ServiceUnavailableException(Throwable cause) {
        super(DEFAULT_MESSAGE, cause);
    }
}
