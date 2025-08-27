package com.nnipa.tenant.exception;

import org.springframework.http.HttpStatus;

public class ValidationException extends TenantException {
    public ValidationException(String message) {
        super(message, "VALIDATION_FAILED", HttpStatus.BAD_REQUEST);
    }
}