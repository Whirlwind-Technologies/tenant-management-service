package com.nnipa.tenant.exception;

import org.springframework.http.HttpStatus; /**
 * Exception thrown when a tenant already exists.
 */
public class TenantAlreadyExistsException extends TenantException {
    public TenantAlreadyExistsException(String subdomain) {
        super(String.format("Tenant already exists with subdomain: %s", subdomain),
                "TENANT_ALREADY_EXISTS",
                HttpStatus.CONFLICT,
                subdomain);
    }
}
