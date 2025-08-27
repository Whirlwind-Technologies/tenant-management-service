package com.nnipa.tenant.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.time.Instant;
import java.util.List;

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

    @Schema(description = "Pagination metadata (only for paginated responses)")
    private PaginationMetadata pagination;

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
     * Create a paginated success response
     */
    public static <T> ApiResponse<List<T>> paginated(Page<T> page) {
        return ApiResponse.<List<T>>builder()
                .status("success")
                .data(page.getContent())
                .pagination(PaginationMetadata.fromPage(page))
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Create a paginated success response with message
     */
    public static <T> ApiResponse<List<T>> paginated(Page<T> page, String message) {
        return ApiResponse.<List<T>>builder()
                .status("success")
                .message(message)
                .data(page.getContent())
                .pagination(PaginationMetadata.fromPage(page))
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
     * Create a not found response
     */
    public static <T> ApiResponse<T> notFound(String message) {
        return ApiResponse.<T>builder()
                .status("error")
                .message(message)
                .error(ErrorDetails.builder()
                        .code("NOT_FOUND")
                        .details(message)
                        .build())
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

    /**
     * Pagination metadata
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Pagination metadata")
    public static class PaginationMetadata {

        @Schema(description = "Current page number (0-indexed)")
        private int page;

        @Schema(description = "Number of items per page")
        private int size;

        @Schema(description = "Total number of elements")
        private long totalElements;

        @Schema(description = "Total number of pages")
        private int totalPages;

        @Schema(description = "Is this the first page")
        private boolean first;

        @Schema(description = "Is this the last page")
        private boolean last;

        @Schema(description = "Number of elements in current page")
        private int numberOfElements;

        /**
         * Create pagination metadata from Spring's Page object
         */
        public static PaginationMetadata fromPage(Page<?> page) {
            return PaginationMetadata.builder()
                    .page(page.getNumber())
                    .size(page.getSize())
                    .totalElements(page.getTotalElements())
                    .totalPages(page.getTotalPages())
                    .first(page.isFirst())
                    .last(page.isLast())
                    .numberOfElements(page.getNumberOfElements())
                    .build();
        }
    }
}