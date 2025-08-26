package com.nnipa.tenant.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base exception class for tenant-related errors.
 * Provides HTTP status code and error code for API responses.
 */
@Getter
public class TenantException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus httpStatus;
    private final Object[] args;

    public TenantException(String message, String errorCode, HttpStatus httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.args = null;
    }

    public TenantException(String message, String errorCode, HttpStatus httpStatus, Object... args) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.args = args;
    }

    public TenantException(String message, Throwable cause, String errorCode, HttpStatus httpStatus) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.args = null;
    }
}