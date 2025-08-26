package com.nnipa.tenant.exception;

import org.springframework.http.HttpStatus; /**
 * Exception thrown when tenant status transition is invalid.
 */
public class InvalidTenantStatusException extends TenantException {
    public InvalidTenantStatusException(String currentStatus, String targetStatus) {
        super(String.format("Invalid status transition from %s to %s", currentStatus, targetStatus),
                "INVALID_STATUS_TRANSITION",
                HttpStatus.BAD_REQUEST,
                currentStatus, targetStatus);
    }
}
