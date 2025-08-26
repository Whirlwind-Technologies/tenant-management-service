package com.nnipa.tenant.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a tenant is not found.
 */
public class TenantNotFoundException extends TenantException {
    public TenantNotFoundException(String tenantId) {
        super(String.format("Tenant not found with ID: %s", tenantId),
                "TENANT_NOT_FOUND",
                HttpStatus.NOT_FOUND,
                tenantId);
    }
}

