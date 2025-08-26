package com.nnipa.tenant.exception;

import org.springframework.http.HttpStatus; /**
 * Exception thrown when tenant limits are exceeded.
 */
public class TenantLimitExceededException extends TenantException {
    public TenantLimitExceededException(String limitType, Integer current, Integer max) {
        super(String.format("Tenant limit exceeded for %s. Current: %d, Max: %d", limitType, current, max),
                "TENANT_LIMIT_EXCEEDED",
                HttpStatus.FORBIDDEN,
                limitType, current, max);
    }
}
