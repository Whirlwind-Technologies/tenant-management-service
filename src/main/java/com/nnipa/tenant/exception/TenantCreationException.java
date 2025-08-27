package com.nnipa.tenant.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when tenant creation fails.
 * This exception is used to wrap other exceptions that occur during tenant creation.
 */
public class TenantCreationException extends TenantException {

    public TenantCreationException(String message) {
        super(message, "TENANT_CREATION_FAILED", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    public TenantCreationException(String message, Throwable cause) {
        super(message, cause, "TENANT_CREATION_FAILED", HttpStatus.INTERNAL_SERVER_ERROR);
    }
}