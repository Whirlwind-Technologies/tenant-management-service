package com.nnipa.tenant.exception;

import org.springframework.http.HttpStatus;

public class DuplicateTenantException extends TenantException {
    public DuplicateTenantException(String message) {
        super(message, "TENANT_ALREADY_EXISTS", HttpStatus.CONFLICT);
    }
}