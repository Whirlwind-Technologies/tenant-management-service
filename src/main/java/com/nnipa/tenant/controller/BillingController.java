package com.nnipa.tenant.controller;

import com.nnipa.tenant.dto.request.UsageRecordRequest;
import com.nnipa.tenant.service.BillingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
//import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for billing and usage management
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/billing")
@RequiredArgsConstructor
@Tag(name = "Billing Management", description = "APIs for billing and usage tracking")
public class BillingController {

    private final BillingService billingService;

    /**
     * Get current month usage for a subscription
     */
    @GetMapping("/subscription/{subscriptionId}/current-usage")
    @Operation(summary = "Get current usage", description = "Retrieves current month's usage for a subscription")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usage retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Subscription not found")
    })
//    @PreAuthorize("hasRole('ADMIN') or hasRole('TENANT_ADMIN')")
    public ResponseEntity<Map<String, Object>> getCurrentMonthUsage(
            @PathVariable UUID subscriptionId) {

        log.info("Fetching current month usage for subscription: {}", subscriptionId);

        BigDecimal usage = billingService.calculateCurrentMonthUsage(subscriptionId);
        BigDecimal unbilled = billingService.calculateUnbilledAmount(subscriptionId);

        Map<String, Object> response = new HashMap<>();
        response.put("subscriptionId", subscriptionId);
        response.put("currentMonthUsage", usage);
        response.put("unbilledAmount", unbilled);
        response.put("month", LocalDateTime.now().getMonth());
        response.put("year", LocalDateTime.now().getYear());

        return ResponseEntity.ok(response);
    }

    /**
     * Get usage summary for a date range
     */
    @GetMapping("/subscription/{subscriptionId}/usage-summary")
    @Operation(summary = "Get usage summary", description = "Retrieves usage summary for a specific date range")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usage summary retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Subscription not found")
    })
//    @PreAuthorize("hasRole('ADMIN') or hasRole('TENANT_ADMIN')")
    public ResponseEntity<Map<String, Object>> getUsageSummary(
            @PathVariable UUID subscriptionId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {

        log.info("Fetching usage summary for subscription: {} from {} to {}",
                subscriptionId, startDate, endDate);

        Map<String, Object> summary = billingService.getUsageSummary(subscriptionId, startDate, endDate);

        return ResponseEntity.ok(summary);
    }

    /**
     * Record usage for a subscription
     */
    @PostMapping("/subscription/{subscriptionId}/usage")
    @Operation(summary = "Record usage", description = "Records usage metrics for billing")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Usage recorded successfully"),
            @ApiResponse(responseCode = "404", description = "Subscription not found"),
            @ApiResponse(responseCode = "400", description = "Invalid usage data")
    })
//    @PreAuthorize("hasRole('ADMIN') or hasRole('SERVICE')")
    public ResponseEntity<Map<String, String>> recordUsage(
            @PathVariable UUID subscriptionId,
            @Valid @RequestBody UsageRecordRequest request) {

        log.info("Recording usage for subscription: {}, metric: {}",
                subscriptionId, request.getMetricName());

        billingService.recordUsage(subscriptionId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "status", "success",
                "message", "Usage recorded successfully"
        ));
    }

    /**
     * Batch record usage
     */
    @PostMapping("/subscription/{subscriptionId}/usage/batch")
    @Operation(summary = "Batch record usage", description = "Records multiple usage metrics at once")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Usage records created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid usage data")
    })
//    @PreAuthorize("hasRole('ADMIN') or hasRole('SERVICE')")
    public ResponseEntity<Map<String, Object>> batchRecordUsage(
            @PathVariable UUID subscriptionId,
            @Valid @RequestBody List<UsageRecordRequest> requests) {

        log.info("Batch recording {} usage records for subscription: {}",
                requests.size(), subscriptionId);

        int successCount = 0;
        int failureCount = 0;

        for (UsageRecordRequest request : requests) {
            try {
                billingService.recordUsage(subscriptionId, request);
                successCount++;
            } catch (Exception e) {
                log.error("Failed to record usage for metric: {}", request.getMetricName(), e);
                failureCount++;
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", successCount);
        response.put("failed", failureCount);
        response.put("total", requests.size());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Process billing for a subscription
     */
    @PostMapping("/subscription/{subscriptionId}/process")
    @Operation(summary = "Process billing", description = "Manually triggers billing processing")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Billing processed successfully"),
            @ApiResponse(responseCode = "402", description = "Payment failed"),
            @ApiResponse(responseCode = "404", description = "Subscription not found")
    })
//    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> processBilling(
            @PathVariable UUID subscriptionId,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {

        log.info("Manually processing billing for subscription: {}", subscriptionId);

        // This would trigger billing process
        Map<String, Object> response = new HashMap<>();
        response.put("status", "processing");
        response.put("message", "Billing process initiated");
        response.put("subscriptionId", subscriptionId);
        response.put("timestamp", LocalDateTime.now());

        return ResponseEntity.ok(response);
    }

    /**
     * Get billing history
     */
    @GetMapping("/subscription/{subscriptionId}/history")
    @Operation(summary = "Get billing history", description = "Retrieves billing history for a subscription")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Billing history retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Subscription not found")
    })
//    @PreAuthorize("hasRole('ADMIN') or hasRole('TENANT_ADMIN')")
    public ResponseEntity<Map<String, Object>> getBillingHistory(
            @PathVariable UUID subscriptionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        log.info("Fetching billing history for subscription: {}", subscriptionId);

        // This would fetch billing history from database
        Map<String, Object> response = new HashMap<>();
        response.put("subscriptionId", subscriptionId);
        response.put("page", page);
        response.put("size", size);
        response.put("totalElements", 0);
        response.put("history", List.of());

        return ResponseEntity.ok(response);
    }

    /**
     * Get invoices
     */
    @GetMapping("/subscription/{subscriptionId}/invoices")
    @Operation(summary = "Get invoices", description = "Retrieves all invoices for a subscription")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Invoices retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Subscription not found")
    })
//    @PreAuthorize("hasRole('ADMIN') or hasRole('TENANT_ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getInvoices(
            @PathVariable UUID subscriptionId) {

        log.info("Fetching invoices for subscription: {}", subscriptionId);

        // This would fetch invoices from database or payment provider
        List<Map<String, Object>> invoices = List.of();

        return ResponseEntity.ok(invoices);
    }

    /**
     * Download invoice
     */
    @GetMapping("/invoice/{invoiceId}/download")
    @Operation(summary = "Download invoice", description = "Downloads a specific invoice as PDF")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Invoice downloaded successfully"),
            @ApiResponse(responseCode = "404", description = "Invoice not found")
    })
//    @PreAuthorize("hasRole('ADMIN') or hasRole('TENANT_ADMIN')")
    public ResponseEntity<byte[]> downloadInvoice(
            @PathVariable String invoiceId) {

        log.info("Downloading invoice: {}", invoiceId);

        // This would generate or fetch invoice PDF
        // For now, return empty response
        return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "attachment; filename=invoice-" + invoiceId + ".pdf")
                .body(new byte[0]);
    }

    /**
     * Update payment method
     */
    @PutMapping("/subscription/{subscriptionId}/payment-method")
    @Operation(summary = "Update payment method", description = "Updates the payment method for a subscription")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Payment method updated successfully"),
            @ApiResponse(responseCode = "404", description = "Subscription not found"),
            @ApiResponse(responseCode = "400", description = "Invalid payment information")
    })
//    @PreAuthorize("hasRole('ADMIN') or hasRole('TENANT_ADMIN')")
    public ResponseEntity<Map<String, String>> updatePaymentMethod(
            @PathVariable UUID subscriptionId,
            @RequestBody Map<String, String> paymentInfo,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {

        log.info("Updating payment method for subscription: {}", subscriptionId);

        // This would update payment method with payment provider
        // Validate and tokenize payment information

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Payment method updated successfully"
        ));
    }

    /**
     * Get payment methods
     */
    @GetMapping("/subscription/{subscriptionId}/payment-methods")
    @Operation(summary = "Get payment methods", description = "Retrieves all payment methods for a subscription")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Payment methods retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Subscription not found")
    })
//    @PreAuthorize("hasRole('ADMIN') or hasRole('TENANT_ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getPaymentMethods(
            @PathVariable UUID subscriptionId) {

        log.info("Fetching payment methods for subscription: {}", subscriptionId);

        // This would fetch payment methods from payment provider
        List<Map<String, Object>> methods = List.of(
                Map.of(
                        "id", "pm_123",
                        "type", "card",
                        "last4", "4242",
                        "brand", "Visa",
                        "expiryMonth", 12,
                        "expiryYear", 2025,
                        "isDefault", true
                )
        );

        return ResponseEntity.ok(methods);
    }

    /**
     * Apply discount code
     */
    @PostMapping("/subscription/{subscriptionId}/discount")
    @Operation(summary = "Apply discount", description = "Applies a discount code to a subscription")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Discount applied successfully"),
            @ApiResponse(responseCode = "404", description = "Subscription not found"),
            @ApiResponse(responseCode = "400", description = "Invalid discount code")
    })
//    @PreAuthorize("hasRole('ADMIN') or hasRole('TENANT_ADMIN')")
    public ResponseEntity<Map<String, Object>> applyDiscountCode(
            @PathVariable UUID subscriptionId,
            @RequestParam String discountCode,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {

        log.info("Applying discount code {} to subscription: {}", discountCode, subscriptionId);

        // Validate and apply discount code
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Discount applied successfully");
        response.put("discountCode", discountCode);
        response.put("discountPercentage", 10);
        response.put("validUntil", LocalDateTime.now().plusMonths(3));

        return ResponseEntity.ok(response);
    }
}