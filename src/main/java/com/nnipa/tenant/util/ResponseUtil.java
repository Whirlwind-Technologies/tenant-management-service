package com.nnipa.tenant.util;

import com.nnipa.tenant.dto.response.ApiResponse;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Utility class for building consistent API responses
 */
public class ResponseUtil {

    private static final ThreadLocal<String> requestIdHolder = new ThreadLocal<>();

    /**
     * Set request ID for current thread
     */
    public static void setRequestId(String requestId) {
        requestIdHolder.set(requestId);
    }

    /**
     * Get request ID for current thread
     */
    public static String getRequestId() {
        String requestId = requestIdHolder.get();
        return requestId != null ? requestId : generateRequestId();
    }

    /**
     * Clear request ID for current thread
     */
    public static void clearRequestId() {
        requestIdHolder.remove();
    }

    /**
     * Generate a new request ID
     */
    public static String generateRequestId() {
        return "req_" + UUID.randomUUID().toString();
    }

    /**
     * Build success response
     */
    public static <T> ResponseEntity<ApiResponse<T>> success(T data) {
        ApiResponse<T> response = ApiResponse.success(data);
        response.setRequestId(getRequestId());
        return ResponseEntity.ok(response);
    }

    /**
     * Build success response with message
     */
    public static <T> ResponseEntity<ApiResponse<T>> success(T data, String message) {
        ApiResponse<T> response = ApiResponse.success(data, message);
        response.setRequestId(getRequestId());
        return ResponseEntity.ok(response);
    }

    /**
     * Build created response
     */
    public static <T> ResponseEntity<ApiResponse<T>> created(T data) {
        ApiResponse<T> response = ApiResponse.success(data, "Resource created successfully");
        response.setRequestId(getRequestId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Build created response with message
     */
    public static <T> ResponseEntity<ApiResponse<T>> created(T data, String message) {
        ApiResponse<T> response = ApiResponse.success(data, message);
        response.setRequestId(getRequestId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Build paginated response
     */
    public static <T> ResponseEntity<ApiResponse<java.util.List<T>>> paginated(Page<T> page) {
        ApiResponse<java.util.List<T>> response = ApiResponse.paginated(page);
        response.setRequestId(getRequestId());
        return ResponseEntity.ok(response);
    }

    /**
     * Build response from Optional
     */
    public static <T, R> ResponseEntity<ApiResponse<R>> fromOptional(
            Optional<T> optional,
            Function<T, R> mapper,
            String notFoundMessage) {
        return optional
                .map(mapper)
                .map(ResponseUtil::success)
                .orElseGet(() -> notFound(notFoundMessage));
    }

    /**
     * Build not found response
     */
    public static <T> ResponseEntity<ApiResponse<T>> notFound(String message) {
        ApiResponse<T> response = ApiResponse.notFound(message);
        response.setRequestId(getRequestId());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    /**
     * Build no content response (for delete operations)
     */
    public static ResponseEntity<ApiResponse<Void>> noContent(String message) {
        ApiResponse<Void> response = ApiResponse.success(null, message);
        response.setRequestId(getRequestId());
        return ResponseEntity.status(HttpStatus.NO_CONTENT).body(response);
    }
}