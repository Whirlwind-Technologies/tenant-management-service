package com.nnipa.tenant.controller;

import com.nnipa.tenant.dto.request.CreateSubscriptionRequest;
import com.nnipa.tenant.dto.request.ChangePlanRequest;
import com.nnipa.tenant.dto.request.RecordUsageRequest;
import com.nnipa.tenant.dto.response.SubscriptionResponse;
import com.nnipa.tenant.dto.response.UsageResponse;
import com.nnipa.tenant.entity.BillingDetails;
import com.nnipa.tenant.entity.Subscription;
import com.nnipa.tenant.entity.Tenant;
import com.nnipa.tenant.entity.UsageRecord;
import com.nnipa.tenant.enums.SubscriptionPlan;
import com.nnipa.tenant.mapper.SubscriptionMapper;
import com.nnipa.tenant.service.SubscriptionService;
import com.nnipa.tenant.service.TenantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST API controller for subscription management.
 * Handles subscription lifecycle, billing, and usage tracking.
 */
@Slf4j
@RestController
@RequestMapping("/subscriptions")
@RequiredArgsConstructor
@Tag(name = "Subscription Management", description = "APIs for managing subscriptions and billing")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final TenantService tenantService;
    private final SubscriptionMapper subscriptionMapper;

    /**
     * Creates a new subscription for a tenant.
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('BILLING_MANAGER')")
    @Operation(summary = "Create subscription",
            description = "Creates a new subscription for a tenant with specified plan")
    public ResponseEntity<SubscriptionResponse> createSubscription(
            @Valid @RequestBody CreateSubscriptionRequest request) {

        log.info("Creating subscription for tenant: {} with plan: {}",
                request.getTenantId(), request.getPlan());

        Tenant tenant = tenantService.getTenantById(request.getTenantId())
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        BillingDetails billingDetails = subscriptionMapper.toBillingDetails(request.getBillingDetails());
        Subscription subscription = subscriptionService.createSubscription(
                tenant, request.getPlan(), billingDetails);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(subscriptionMapper.toResponse(subscription));
    }

    /**
     * Gets subscription details.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @subscriptionSecurity.canAccessSubscription(#id)")
    @Operation(summary = "Get subscription",
            description = "Retrieves subscription details by ID")
    public ResponseEntity<SubscriptionResponse> getSubscription(@PathVariable UUID id) {
        log.debug("Fetching subscription: {}", id);

        // Implementation would fetch from repository
        return ResponseEntity.ok().build();
    }

    /**
     * Gets subscription for a tenant.
     */
    @GetMapping("/tenant/{tenantId}")
    @PreAuthorize("hasRole('ADMIN') or @tenantSecurity.canAccessTenant(#tenantId)")
    @Operation(summary = "Get tenant subscription",
            description = "Retrieves active subscription for a tenant")
    public ResponseEntity<SubscriptionResponse> getTenantSubscription(@PathVariable UUID tenantId) {
        log.debug("Fetching subscription for tenant: {}", tenantId);

        // Implementation would fetch active subscription
        return ResponseEntity.ok().build();
    }

    /**
     * Changes subscription plan.
     */
    @PutMapping("/{id}/plan")
    @PreAuthorize("hasRole('ADMIN') or @subscriptionSecurity.isSubscriptionOwner(#id)")
    @Operation(summary = "Change plan",
            description = "Upgrades or downgrades subscription plan")
    public ResponseEntity<SubscriptionResponse> changePlan(
            @PathVariable UUID id,
            @Valid @RequestBody ChangePlanRequest request) {

        log.info("Changing plan for subscription: {} to {}", id, request.getNewPlan());

        Subscription subscription = subscriptionService.changePlan(id, request.getNewPlan());
        return ResponseEntity.ok(subscriptionMapper.toResponse(subscription));
    }

    /**
     * Cancels a subscription.
     */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('ADMIN') or @subscriptionSecurity.isSubscriptionOwner(#id)")
    @Operation(summary = "Cancel subscription",
            description = "Cancels an active subscription")
    public ResponseEntity<SubscriptionResponse> cancelSubscription(
            @PathVariable UUID id,
            @RequestParam String reason) {

        log.info("Canceling subscription: {} (Reason: {})", id, reason);

        Subscription subscription = subscriptionService.cancelSubscription(id, reason);
        return ResponseEntity.ok(subscriptionMapper.toResponse(subscription));
    }

    /**
     * Renews a subscription.
     */
    @PostMapping("/{id}/renew")
    @PreAuthorize("hasRole('ADMIN') or hasRole('BILLING_MANAGER')")
    @Operation(summary = "Renew subscription",
            description = "Manually renews a subscription")
    public ResponseEntity<SubscriptionResponse> renewSubscription(@PathVariable UUID id) {
        log.info("Renewing subscription: {}", id);

        Subscription subscription = subscriptionService.renewSubscription(id);
        return ResponseEntity.ok(subscriptionMapper.toResponse(subscription));
    }

    /**
     * Records usage for a subscription.
     */
    @PostMapping("/{id}/usage")
    @PreAuthorize("hasRole('ADMIN') or @subscriptionSecurity.canRecordUsage(#id)")
    @Operation(summary = "Record usage",
            description = "Records resource usage for billing purposes")
    public ResponseEntity<UsageResponse> recordUsage(
            @PathVariable UUID id,
            @Valid @RequestBody RecordUsageRequest request) {

        log.info("Recording usage for subscription: {} - {} {} {}",
                id, request.getQuantity(), request.getUnit(), request.getMetricName());

        UsageRecord record = subscriptionService.recordUsage(
                id,
                request.getMetricName(),
                request.getQuantity(),
                request.getUnit()
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(subscriptionMapper.toUsageResponse(record));
    }

    /**
     * Gets current usage for a subscription.
     */
    @GetMapping("/{id}/usage/current")
    @PreAuthorize("hasRole('ADMIN') or @subscriptionSecurity.canAccessSubscription(#id)")
    @Operation(summary = "Get current usage",
            description = "Retrieves current billing period usage statistics")
    public ResponseEntity<SubscriptionService.SubscriptionUsage> getCurrentUsage(
            @PathVariable UUID id) {

        log.debug("Fetching current usage for subscription: {}", id);

        SubscriptionService.SubscriptionUsage usage = subscriptionService.getCurrentUsage(id);
        return ResponseEntity.ok(usage);
    }

    /**
     * Gets historical usage for a subscription.
     */
    @GetMapping("/{id}/usage/history")
    @PreAuthorize("hasRole('ADMIN') or @subscriptionSecurity.canAccessSubscription(#id)")
    @Operation(summary = "Get usage history",
            description = "Retrieves historical usage data for specified date range")
    public ResponseEntity<List<UsageResponse>> getUsageHistory(
            @PathVariable UUID id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        log.debug("Fetching usage history for subscription: {} from {} to {}",
                id, startDate, endDate);

        // Implementation would fetch historical records
        return ResponseEntity.ok().build();
    }

    /**
     * Gets available plans for upgrade.
     */
    @GetMapping("/{id}/available-plans")
    @PreAuthorize("hasRole('ADMIN') or @subscriptionSecurity.canAccessSubscription(#id)")
    @Operation(summary = "Get available plans",
            description = "Lists plans available for upgrade from current plan")
    public ResponseEntity<List<SubscriptionPlan>> getAvailablePlans(@PathVariable UUID id) {
        log.debug("Fetching available plans for subscription: {}", id);

        // Implementation would return upgrade options
        return ResponseEntity.ok().build();
    }

    /**
     * Applies a discount to subscription.
     */
    @PostMapping("/{id}/discount")
    @PreAuthorize("hasRole('ADMIN') or hasRole('BILLING_MANAGER')")
    @Operation(summary = "Apply discount",
            description = "Applies a discount percentage to the subscription")
    public ResponseEntity<SubscriptionResponse> applyDiscount(
            @PathVariable UUID id,
            @RequestParam BigDecimal percentage,
            @RequestParam String reason) {

        log.info("Applying {}% discount to subscription: {} (Reason: {})",
                percentage, id, reason);

        // Implementation would apply discount
        return ResponseEntity.ok().build();
    }

    /**
     * Gets expiring subscriptions.
     */
    @GetMapping("/expiring")
    @PreAuthorize("hasRole('ADMIN') or hasRole('BILLING_MANAGER')")
    @Operation(summary = "Get expiring subscriptions",
            description = "Lists subscriptions expiring within specified days")
    public ResponseEntity<List<SubscriptionResponse>> getExpiringSubscriptions(
            @RequestParam(defaultValue = "30") int daysAhead) {

        log.debug("Fetching subscriptions expiring in {} days", daysAhead);

        // Implementation would fetch expiring subscriptions
        return ResponseEntity.ok().build();
    }

    /**
     * Gets subscriptions by status.
     */
    @GetMapping("/status/{status}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('BILLING_MANAGER')")
    @Operation(summary = "Get subscriptions by status",
            description = "Lists all subscriptions with specified status")
    public ResponseEntity<List<SubscriptionResponse>> getSubscriptionsByStatus(
            @PathVariable String status) {

        log.debug("Fetching subscriptions with status: {}", status);

        // Implementation would fetch by status
        return ResponseEntity.ok().build();
    }

    /**
     * Generates invoice for subscription.
     */
    @PostMapping("/{id}/invoice")
    @PreAuthorize("hasRole('ADMIN') or hasRole('BILLING_MANAGER')")
    @Operation(summary = "Generate invoice",
            description = "Generates an invoice for the current billing period")
    public ResponseEntity<Void> generateInvoice(@PathVariable UUID id) {
        log.info("Generating invoice for subscription: {}", id);

        // Implementation would generate and send invoice
        return ResponseEntity.accepted().build();
    }

    /**
     * Updates billing details.
     */
    @PutMapping("/{id}/billing")
    @PreAuthorize("hasRole('ADMIN') or @subscriptionSecurity.isSubscriptionOwner(#id)")
    @Operation(summary = "Update billing details",
            description = "Updates payment method and billing information")
    public ResponseEntity<SubscriptionResponse> updateBillingDetails(
            @PathVariable UUID id,
            @Valid @RequestBody BillingDetails billingDetails) {

        log.info("Updating billing details for subscription: {}", id);

        // Implementation would update billing details
        return ResponseEntity.ok().build();
    }

    /**
     * Self-service endpoint for current tenant's subscription.
     */
    @GetMapping("/me")
    @Operation(summary = "Get my subscription",
            description = "Retrieves subscription for authenticated tenant")
    public ResponseEntity<SubscriptionResponse> getMySubscription(
            @RequestHeader("X-Tenant-ID") String tenantId) {

        log.debug("Fetching subscription for current tenant: {}", tenantId);

        // Implementation would fetch subscription for current tenant
        return ResponseEntity.ok().build();
    }
}