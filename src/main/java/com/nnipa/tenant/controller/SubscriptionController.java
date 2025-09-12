package com.nnipa.tenant.controller;

import com.nnipa.tenant.dto.request.UpdateSubscriptionRequest;
import com.nnipa.tenant.dto.response.SubscriptionResponse;
import com.nnipa.tenant.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
//import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for subscription management
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
@Tag(name = "Subscription Management", description = "APIs for managing subscriptions")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    /**
     * Get subscription by ID
     */
    @GetMapping("/{subscriptionId}")
    @Operation(summary = "Get subscription by ID", description = "Retrieves subscription details by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Subscription found"),
            @ApiResponse(responseCode = "404", description = "Subscription not found")
    })
//    @PreAuthorize("hasRole('ADMIN') or hasRole('TENANT_ADMIN')")
    public ResponseEntity<SubscriptionResponse> getSubscription(
            @PathVariable UUID subscriptionId) {

        log.info("Fetching subscription: {}", subscriptionId);

        // For now, get by tenant ID (subscription ID is same as tenant ID in many cases)
        SubscriptionResponse response = subscriptionService.getSubscriptionByTenantId(subscriptionId);
        return ResponseEntity.ok(response);
    }

    /**
     * Get subscription by tenant ID
     */
    @GetMapping("/tenant/{tenantId}")
    @Operation(summary = "Get subscription by tenant ID", description = "Retrieves subscription for a specific tenant")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Subscription found"),
            @ApiResponse(responseCode = "404", description = "Subscription not found")
    })
//    @PreAuthorize("hasRole('ADMIN') or (hasRole('TENANT_ADMIN') and #tenantId == authentication.principal.tenantId)")
    public ResponseEntity<SubscriptionResponse> getSubscriptionByTenant(
            @PathVariable UUID tenantId) {

        log.info("Fetching subscription for tenant: {}", tenantId);
        SubscriptionResponse response = subscriptionService.getSubscriptionByTenantId(tenantId);
        return ResponseEntity.ok(response);
    }

    /**
     * Update subscription
     */
    @PutMapping("/{tenantId}")
    @Operation(summary = "Update subscription", description = "Updates subscription details")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Subscription updated successfully"),
            @ApiResponse(responseCode = "404", description = "Subscription not found"),
            @ApiResponse(responseCode = "400", description = "Invalid request data")
    })
//    @PreAuthorize("hasRole('ADMIN') or (hasRole('TENANT_ADMIN') and #tenantId == authentication.principal.tenantId)")
    public ResponseEntity<SubscriptionResponse> updateSubscription(
            @PathVariable UUID tenantId,
            @Valid @RequestBody UpdateSubscriptionRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {

        log.info("Updating subscription for tenant: {}", tenantId);
        SubscriptionResponse response = subscriptionService.updateSubscription(tenantId, request,
                userId != null ? userId : "system");
        return ResponseEntity.ok(response);
    }

    /**
     * Cancel subscription
     */
    @PostMapping("/{tenantId}/cancel")
    @Operation(summary = "Cancel subscription", description = "Cancels an active subscription")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Subscription cancelled successfully"),
            @ApiResponse(responseCode = "404", description = "Subscription not found")
    })
//    @PreAuthorize("hasRole('ADMIN') or (hasRole('TENANT_ADMIN') and #tenantId == authentication.principal.tenantId)")
    public ResponseEntity<Map<String, String>> cancelSubscription(
            @PathVariable UUID tenantId,
            @RequestParam(required = false) String reason,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {

        log.info("Cancelling subscription for tenant: {}, reason: {}", tenantId, reason);
        subscriptionService.cancelSubscription(tenantId, reason != null ? reason : "User requested cancellation");

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Subscription cancelled successfully"
        ));
    }

    /**
     * Pause subscription
     */
    @PostMapping("/{tenantId}/pause")
    @Operation(summary = "Pause subscription", description = "Temporarily pauses a subscription")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Subscription paused successfully"),
            @ApiResponse(responseCode = "404", description = "Subscription not found")
    })
//    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> pauseSubscription(
            @PathVariable UUID tenantId,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {

        log.info("Pausing subscription for tenant: {}", tenantId);
        subscriptionService.pauseSubscription(tenantId);

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Subscription paused successfully"
        ));
    }

    /**
     * Resume subscription
     */
    @PostMapping("/{tenantId}/resume")
    @Operation(summary = "Resume subscription", description = "Resumes a paused subscription")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Subscription resumed successfully"),
            @ApiResponse(responseCode = "404", description = "Subscription not found")
    })
//    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> resumeSubscription(
            @PathVariable UUID tenantId,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {

        log.info("Resuming subscription for tenant: {}", tenantId);
        subscriptionService.resumeSubscription(tenantId);

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Subscription resumed successfully"
        ));
    }

    /**
     * Convert trial to paid subscription
     */
    @PostMapping("/{tenantId}/convert-trial")
    @Operation(summary = "Convert trial", description = "Converts a trial subscription to paid")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Trial converted successfully"),
            @ApiResponse(responseCode = "404", description = "Subscription not found"),
            @ApiResponse(responseCode = "400", description = "Subscription is not in trial status")
    })
//    @PreAuthorize("hasRole('ADMIN') or (hasRole('TENANT_ADMIN') and #tenantId == authentication.principal.tenantId)")
    public ResponseEntity<Map<String, String>> convertTrialToPaid(
            @PathVariable UUID tenantId,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {

        log.info("Converting trial to paid for tenant: {}", tenantId);
        subscriptionService.convertTrialToPaid(tenantId);

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Trial converted to paid subscription successfully"
        ));
    }

    /**
     * Process subscription renewal
     */
    @PostMapping("/{tenantId}/renew")
    @Operation(summary = "Renew subscription", description = "Manually triggers subscription renewal")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Subscription renewed successfully"),
            @ApiResponse(responseCode = "404", description = "Subscription not found"),
            @ApiResponse(responseCode = "402", description = "Payment required")
    })
//    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> renewSubscription(
            @PathVariable UUID tenantId,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {

        log.info("Manually renewing subscription for tenant: {}", tenantId);

        // This would typically process payment and renew
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Subscription renewal initiated");
        response.put("nextRenewalDate", LocalDateTime.now().plusMonths(1));

        return ResponseEntity.ok(response);
    }

    /**
     * Get subscription statistics
     */
    @GetMapping("/statistics")
    @Operation(summary = "Get subscription statistics", description = "Returns statistics about subscriptions")
//    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getSubscriptionStatistics() {
        log.info("Fetching subscription statistics");

        Map<String, Object> stats = new HashMap<>();
        // Add subscription statistics
        stats.put("totalSubscriptions", 100); // Example
        stats.put("activeSubscriptions", 85);
        stats.put("trialSubscriptions", 10);
        stats.put("cancelledSubscriptions", 5);

        return ResponseEntity.ok(stats);
    }
}