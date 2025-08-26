package com.nnipa.tenant.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Generic API response wrapper for consistent response format
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standard API response wrapper")
public class ApiResponse<T> {

    @Schema(description = "Response status", example = "success")
    private String status;

    @Schema(description = "Response message", example = "Operation completed successfully")
    private String message;

    @Schema(description = "Response data payload")
    private T data;

    @Schema(description = "Error details (only present on error)")
    private ErrorDetails error;

    @Schema(description = "Response timestamp")
    private Instant timestamp;

    @Schema(description = "Request tracking ID", example = "req_123e4567-e89b-12d3-a456-426614174000")
    private String requestId;

    /**
     * Create a success response
     */
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .status("success")
                .data(data)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Create a success response with message
     */
    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder()
                .status("success")
                .message(message)
                .data(data)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Create an error response
     */
    public static <T> ApiResponse<T> error(String message, ErrorDetails error) {
        return ApiResponse.<T>builder()
                .status("error")
                .message(message)
                .error(error)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Error details inner class
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Error details")
    public static class ErrorDetails {

        @Schema(description = "Error code", example = "TENANT_NOT_FOUND")
        private String code;

        @Schema(description = "Detailed error message")
        private String details;

        @Schema(description = "Field that caused the error (for validation errors)")
        private String field;

        @Schema(description = "Stack trace (only in development mode)")
        private String stackTrace;
    }
}