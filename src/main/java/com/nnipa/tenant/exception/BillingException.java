package com.nnipa.tenant.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus; /**
 * Exception for billing related errors
 */
@ResponseStatus(HttpStatus.PAYMENT_REQUIRED)
public class BillingException extends RuntimeException {
    public BillingException(String message) {
        super(message);
    }
}
