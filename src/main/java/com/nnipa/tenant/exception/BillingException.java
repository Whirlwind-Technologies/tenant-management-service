package com.nnipa.tenant.exception;

public class BillingException extends RuntimeException {

    public BillingException(String message) {
        super(message);
    }

    public BillingException(String message, Throwable cause) {
        super(message, cause);
    }
}