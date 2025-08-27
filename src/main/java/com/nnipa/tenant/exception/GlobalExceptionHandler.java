package com.nnipa.tenant.exception;

import com.nnipa.tenant.dto.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Global exception handler for REST API
 * Provides centralized error handling and consistent error responses
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handle TenantNotFoundException
     */
    @ExceptionHandler(TenantNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleTenantNotFoundException(
            TenantNotFoundException ex, HttpServletRequest request) {

        log.error("Tenant not found: {}", ex.getMessage());

        ApiResponse<Void> response = ApiResponse.error(
                ex.getMessage(),
                ApiResponse.ErrorDetails.builder()
                        .code(ex.getErrorCode())
                        .details(ex.getMessage())
                        .build()
        );
        response.setRequestId(generateRequestId());

        return ResponseEntity.status(ex.getHttpStatus()).body(response);
    }

    /**
     * Handle TenantAlreadyExistsException
     */
    @ExceptionHandler(TenantAlreadyExistsException.class)
    public ResponseEntity<ApiResponse<Void>> handleTenantAlreadyExistsException(
            TenantAlreadyExistsException ex, HttpServletRequest request) {

        log.error("Tenant already exists: {}", ex.getMessage());

        ApiResponse<Void> response = ApiResponse.error(
                ex.getMessage(),
                ApiResponse.ErrorDetails.builder()
                        .code(ex.getErrorCode())
                        .details(ex.getMessage())
                        .build()
        );
        response.setRequestId(generateRequestId());

        return ResponseEntity.status(ex.getHttpStatus()).body(response);
    }

    /**
     * Handle TenantValidationException
     */
    @ExceptionHandler(TenantValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleTenantValidationException(
            TenantValidationException ex, HttpServletRequest request) {

        log.error("Tenant validation failed: {}", ex.getMessage());

        ApiResponse<Void> response = ApiResponse.error(
                "Validation failed",
                ApiResponse.ErrorDetails.builder()
                        .code(ex.getErrorCode())
                        .details(ex.getMessage())
                        .build()
        );
        response.setRequestId(generateRequestId());

        return ResponseEntity.status(ex.getHttpStatus()).body(response);
    }

    /**
     * Handle TenantLimitExceededException
     */
    @ExceptionHandler(TenantLimitExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleTenantLimitExceededException(
            TenantLimitExceededException ex, HttpServletRequest request) {

        log.error("Tenant limit exceeded: {}", ex.getMessage());

        ApiResponse<Void> response = ApiResponse.error(
                "Resource limit exceeded",
                ApiResponse.ErrorDetails.builder()
                        .code(ex.getErrorCode())
                        .details(ex.getMessage())
                        .build()
        );
        response.setRequestId(generateRequestId());

        return ResponseEntity.status(ex.getHttpStatus()).body(response);
    }

    /**
     * Handle InvalidTenantStatusException
     */
    @ExceptionHandler(InvalidTenantStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidTenantStatusException(
            InvalidTenantStatusException ex, HttpServletRequest request) {

        log.error("Invalid tenant status transition: {}", ex.getMessage());

        ApiResponse<Void> response = ApiResponse.error(
                "Invalid status transition",
                ApiResponse.ErrorDetails.builder()
                        .code(ex.getErrorCode())
                        .details(ex.getMessage())
                        .build()
        );
        response.setRequestId(generateRequestId());

        return ResponseEntity.status(ex.getHttpStatus()).body(response);
    }

    /**
     * Handle TenantProvisioningException
     */
    @ExceptionHandler(TenantProvisioningException.class)
    public ResponseEntity<ApiResponse<Void>> handleTenantProvisioningException(
            TenantProvisioningException ex, HttpServletRequest request) {

        log.error("Tenant provisioning failed: {}", ex.getMessage(), ex);

        ApiResponse<Void> response = ApiResponse.error(
                "Provisioning failed",
                ApiResponse.ErrorDetails.builder()
                        .code(ex.getErrorCode())
                        .details(ex.getMessage())
                        .build()
        );
        response.setRequestId(generateRequestId());

        return ResponseEntity.status(ex.getHttpStatus()).body(response);
    }

    /**
     * Handle SubscriptionException
     */
    @ExceptionHandler(SubscriptionException.class)
    public ResponseEntity<ApiResponse<Void>> handleSubscriptionException(
            SubscriptionException ex, HttpServletRequest request) {

        log.error("Subscription error: {}", ex.getMessage());

        ApiResponse<Void> response = ApiResponse.error(
                "Subscription operation failed",
                ApiResponse.ErrorDetails.builder()
                        .code(ex.getErrorCode())
                        .details(ex.getMessage())
                        .build()
        );
        response.setRequestId(generateRequestId());

        return ResponseEntity.status(ex.getHttpStatus()).body(response);
    }

    /**
     * Handle validation errors from @Valid annotation
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationExceptions(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        log.error("Validation error occurred");

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = error instanceof FieldError ?
                    ((FieldError) error).getField() : error.getObjectName();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        ApiResponse<Map<String, String>> response = ApiResponse.error(
                "Validation failed",
                ApiResponse.ErrorDetails.builder()
                        .code("VALIDATION_ERROR")
                        .details("One or more fields have validation errors")
                        .build()
        );
        response.setData(errors);
        response.setRequestId(generateRequestId());

        log.debug("Validation errors: {}", errors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Handle type mismatch exceptions (e.g., wrong path variable type)
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatchException(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {

        log.error("Type mismatch error: {}", ex.getMessage());

        String error = String.format("Parameter '%s' should be of type %s",
                ex.getName(),
                ex.getRequiredType() != null ?
                        ex.getRequiredType().getSimpleName() : "unknown");

        ApiResponse<Void> response = ApiResponse.error(
                "Invalid parameter type",
                ApiResponse.ErrorDetails.builder()
                        .code("TYPE_MISMATCH")
                        .field(ex.getName())
                        .details(error)
                        .build()
        );
        response.setRequestId(generateRequestId());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Handle IllegalArgumentException
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(
            IllegalArgumentException ex, HttpServletRequest request) {

        log.error("Illegal argument: {}", ex.getMessage());

        ApiResponse<Void> response = ApiResponse.error(
                "Invalid argument",
                ApiResponse.ErrorDetails.builder()
                        .code("ILLEGAL_ARGUMENT")
                        .details(ex.getMessage())
                        .build()
        );
        response.setRequestId(generateRequestId());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Handle DataIntegrityViolationException (database constraint violations)
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolationException(
            DataIntegrityViolationException ex, HttpServletRequest request) {

        log.error("Data integrity violation: {}", ex.getMessage());

        String message = "Data integrity violation";
        String details = "A database constraint was violated";

        // Try to extract more specific information
        if (ex.getMessage() != null) {
            if (ex.getMessage().contains("duplicate key")) {
                message = "Duplicate entry";
                details = "A record with this information already exists";
            } else if (ex.getMessage().contains("foreign key")) {
                message = "Reference constraint violation";
                details = "This operation would violate a reference constraint";
            } else if (ex.getMessage().contains("cannot be null")) {
                message = "Required field missing";
                details = "A required field is missing or null";
            }
        }

        ApiResponse<Void> response = ApiResponse.error(
                message,
                ApiResponse.ErrorDetails.builder()
                        .code("DATA_INTEGRITY_VIOLATION")
                        .details(details)
                        .build()
        );
        response.setRequestId(generateRequestId());

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    /**
     * Handle NoHandlerFoundException (404 errors)
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoHandlerFoundException(
            NoHandlerFoundException ex, HttpServletRequest request) {

        log.error("No handler found for {} {}", ex.getHttpMethod(), ex.getRequestURL());

        ApiResponse<Void> response = ApiResponse.error(
                "Resource not found",
                ApiResponse.ErrorDetails.builder()
                        .code("NOT_FOUND")
                        .details(String.format("No endpoint found for %s %s",
                                ex.getHttpMethod(), ex.getRequestURL()))
                        .build()
        );
        response.setRequestId(generateRequestId());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    /**
     * Handle all other uncaught exceptions
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(
            Exception ex, HttpServletRequest request) {

        log.error("Unexpected error occurred: {}", ex.getMessage(), ex);

        // In development, you might want to include the stack trace
        String stackTrace = null;
        if (isDebugMode()) {
            stackTrace = getStackTraceAsString(ex);
        }

        ApiResponse<Void> response = ApiResponse.error(
                "An unexpected error occurred",
                ApiResponse.ErrorDetails.builder()
                        .code("INTERNAL_SERVER_ERROR")
                        .details("An unexpected error occurred. Please try again later.")
                        .stackTrace(stackTrace)
                        .build()
        );
        response.setRequestId(generateRequestId());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    /**
     * Generate a unique request ID for tracking
     */
    private String generateRequestId() {
        return "req_" + UUID.randomUUID().toString();
    }

    /**
     * Check if application is in debug mode
     */
    private boolean isDebugMode() {
        String profile = System.getProperty("spring.profiles.active", "");
        return profile.contains("dev") || profile.contains("test");
    }

    /**
     * Convert exception stack trace to string
     */
    private String getStackTraceAsString(Exception ex) {
        if (!isDebugMode()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(ex.getClass().getName()).append(": ").append(ex.getMessage()).append("\n");

        StackTraceElement[] stackTrace = ex.getStackTrace();
        int maxLines = Math.min(stackTrace.length, 10); // Limit to 10 lines

        for (int i = 0; i < maxLines; i++) {
            sb.append("\tat ").append(stackTrace[i].toString()).append("\n");
        }

        if (stackTrace.length > maxLines) {
            sb.append("\t... ").append(stackTrace.length - maxLines).append(" more");
        }

        return sb.toString();
    }

    @ExceptionHandler(DuplicateTenantException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateTenantException(
            DuplicateTenantException ex, HttpServletRequest request) {

        log.error("Duplicate tenant: {}", ex.getMessage());

        ApiResponse<Void> response = ApiResponse.error(
                ex.getMessage(),
                ApiResponse.ErrorDetails.builder()
                        .code(ex.getErrorCode())
                        .details(ex.getMessage())
                        .build()
        );
        response.setRequestId(generateRequestId());

        return ResponseEntity.status(ex.getHttpStatus()).body(response);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(
            ValidationException ex, HttpServletRequest request) {

        log.error("Validation error: {}", ex.getMessage());

        ApiResponse<Void> response = ApiResponse.error(
                "Validation failed",
                ApiResponse.ErrorDetails.builder()
                        .code(ex.getErrorCode())
                        .details(ex.getMessage())
                        .build()
        );
        response.setRequestId(generateRequestId());

        return ResponseEntity.status(ex.getHttpStatus()).body(response);
    }
}