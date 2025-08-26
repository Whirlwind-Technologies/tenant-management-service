package com.nnipa.tenant.exception;

import org.springframework.http.HttpStatus; /**
 * Exception thrown when tenant validation fails.
 */
public class TenantValidationException extends TenantException {
    public TenantValidationException(String message) {
        super(message, "TENANT_VALIDATION_FAILED", HttpStatus.BAD_REQUEST);
    }
}
