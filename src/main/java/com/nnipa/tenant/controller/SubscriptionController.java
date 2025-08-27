package com.nnipa.tenant.controller;

import com.nnipa.tenant.dto.request.ChangePlanRequest;
import com.nnipa.tenant.dto.request.CreateSubscriptionRequest;
import com.nnipa.tenant.dto.request.RecordUsageRequest;
import com.nnipa.tenant.dto.response.ApiResponse;
import com.nnipa.tenant.dto.response.SubscriptionResponse;
import com.nnipa.tenant.dto.response.UsageResponse;
import com.nnipa.tenant.entity.BillingDetails;
import com.nnipa.tenant.entity.Subscription;
import com.nnipa.tenant.entity.Tenant;
import com.nnipa.tenant.mapper.SubscriptionMapper;
import com.nnipa.tenant.service.SubscriptionService;
import com.nnipa.tenant.service.TenantService;
import com.nnipa.tenant.util.ResponseUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
@Tag(name = "Subscription Management", description = "APIs for managing subscriptions")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final TenantService tenantService;
    private final SubscriptionMapper subscriptionMapper;

    @PostMapping
    @Operation(summary = "Create subscription")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> createSubscription(
            @Valid @RequestBody CreateSubscriptionRequest request) {

        log.info("Creating subscription for tenant: {} with plan: {}",
                request.getTenantId(), request.getPlan());

        Tenant tenant = tenantService.getTenantById(request.getTenantId())
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        BillingDetails billingDetails = subscriptionMapper.toBillingDetails(request.getBillingDetails());
        Subscription subscription = subscriptionService.createSubscription(
                tenant, request.getPlan(), billingDetails);

        return ResponseUtil.created(
                subscriptionMapper.toResponse(subscription),
                String.format("Subscription created with plan: %s", request.getPlan())
        );
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get subscription")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> getSubscription(@PathVariable UUID id) {
        log.debug("Fetching subscription: {}", id);

        return ResponseUtil.fromOptional(
                subscriptionService.getSubscriptionById(id),
                subscriptionMapper::toResponse,
                String.format("Subscription with ID '%s' not found", id)
        );
    }

    @PostMapping("/{id}/change-plan")
    @Operation(summary = "Change subscription plan")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> changePlan(
            @PathVariable UUID id,
            @Valid @RequestBody ChangePlanRequest request) {

        log.info("Changing subscription {} to plan: {}", id, request.getNewPlan());

        Subscription subscription = subscriptionService.changePlan(
                id, request.getNewPlan());

        return ResponseUtil.success(
                subscriptionMapper.toResponse(subscription),
                String.format("Subscription plan changed to: %s", request.getNewPlan())
        );
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel subscription")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> cancelSubscription(
            @PathVariable UUID id,
            @RequestParam String reason) {

        log.info("Canceling subscription: {} (Reason: {})", id, reason);

        Subscription subscription = subscriptionService.cancelSubscription(id, reason);

        return ResponseUtil.success(
                subscriptionMapper.toResponse(subscription),
                "Subscription cancelled successfully"
        );
    }

    @PostMapping("/{id}/renew")
    @Operation(summary = "Renew subscription")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> renewSubscription(@PathVariable UUID id) {
        log.info("Renewing subscription: {}", id);

        Subscription subscription = subscriptionService.renewSubscription(id);

        return ResponseUtil.success(
                subscriptionMapper.toResponse(subscription),
                "Subscription renewed successfully"
        );
    }

    @PostMapping("/{id}/usage")
    @Operation(summary = "Record usage")
    public ResponseEntity<ApiResponse<UsageResponse>> recordUsage(
            @PathVariable UUID id,
            @Valid @RequestBody RecordUsageRequest request) {

        log.debug("Recording usage for subscription: {}", id);

        var usageRecord = subscriptionService.recordUsage(id, request);

        return ResponseUtil.success(
                subscriptionMapper.toUsageResponse(usageRecord),
                "Usage recorded successfully"
        );
    }

    @GetMapping("/{id}/usage")
    @Operation(summary = "Get usage records")
    public ResponseEntity<ApiResponse<List<UsageResponse>>> getUsageRecords(
            @PathVariable UUID id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        log.debug("Fetching usage for subscription: {} from {} to {}", id, startDate, endDate);

        List<UsageResponse> usage = subscriptionService.getUsageRecords(id, startDate, endDate)
                .stream()
                .map(subscriptionMapper::toUsageResponse)
                .collect(Collectors.toList());

        return ResponseUtil.success(usage,
                String.format("Found %d usage records", usage.size()));
    }

    @GetMapping("/me")
    @Operation(summary = "Get my subscription")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> getMySubscription(
            @RequestHeader("X-Tenant-ID") String tenantId) {

        log.debug("Fetching subscription for current tenant: {}", tenantId);

        return ResponseUtil.fromOptional(
                subscriptionService.getTenantActiveSubscription(UUID.fromString(tenantId)),
                subscriptionMapper::toResponse,
                "No active subscription found for current tenant"
        );
    }
}