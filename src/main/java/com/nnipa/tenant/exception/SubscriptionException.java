package com.nnipa.tenant.exception;

import org.springframework.http.HttpStatus; /**
 * Exception thrown when subscription operation fails.
 */
public class SubscriptionException extends TenantException {
    public SubscriptionException(String message) {
        super(message, "SUBSCRIPTION_ERROR", HttpStatus.BAD_REQUEST);
    }
}
