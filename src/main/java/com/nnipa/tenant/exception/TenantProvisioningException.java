package com.nnipa.tenant.exception;

import org.springframework.http.HttpStatus; /**
 * Exception thrown when tenant provisioning fails.
 */
public class TenantProvisioningException extends TenantException {
    public TenantProvisioningException(String message, Throwable cause) {
        super(message, cause, "TENANT_PROVISIONING_FAILED", HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
