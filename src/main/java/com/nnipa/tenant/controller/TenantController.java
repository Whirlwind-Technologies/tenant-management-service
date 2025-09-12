package com.nnipa.tenant.controller;

import com.nnipa.tenant.dto.request.*;
import com.nnipa.tenant.dto.response.*;
import com.nnipa.tenant.enums.OrganizationType;
import com.nnipa.tenant.enums.TenantStatus;
import com.nnipa.tenant.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
//import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for tenant management
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/tenants")
@RequiredArgsConstructor
@Tag(name = "Tenant Management", description = "APIs for managing tenants")
public class TenantController {

    private final TenantService tenantService;
    private final SubscriptionService subscriptionService;
    private final FeatureFlagService featureFlagService;
    private final TenantSettingsService settingsService;
    private final BillingService billingService;

    /**
     * Create a new tenant
     */
    @PostMapping
    @Operation(summary = "Create a new tenant", description = "Creates a new tenant with subscription and settings")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Tenant created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "409", description = "Tenant already exists")
    })
//    @PreAuthorize("hasRole('ADMIN') or hasRole('TENANT_ADMIN')")
    public ResponseEntity<TenantResponse> createTenant(
            @Valid @RequestBody CreateTenantRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {

        log.info("Creating tenant: {}", request.getTenantCode());
        TenantResponse response = tenantService.createTenant(request, userId != null ? userId : "system");
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Update tenant information
     */
    @PutMapping("/{tenantId}")
    @Operation(summary = "Update tenant", description = "Updates tenant information")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tenant updated successfully"),
            @ApiResponse(responseCode = "404", description = "Tenant not found"),
            @ApiResponse(responseCode = "400", description = "Invalid request data")
    })
//    @PreAuthorize("hasRole('ADMIN') or (hasRole('TENANT_ADMIN') and #tenantId == authentication.principal.tenantId)")
    public ResponseEntity<TenantResponse> updateTenant(
            @PathVariable UUID tenantId,
            @Valid @RequestBody UpdateTenantRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {

        log.info("Updating tenant: {}", tenantId);
        TenantResponse response = tenantService.updateTenant(tenantId, request, userId != null ? userId : "system");
        return ResponseEntity.ok(response);
    }

    /**
     * Get tenant by ID
     */
    @GetMapping("/{tenantId}")
    @Operation(summary = "Get tenant by ID", description = "Retrieves tenant information by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tenant found"),
            @ApiResponse(responseCode = "404", description = "Tenant not found")
    })
//    @PreAuthorize("hasRole('ADMIN') or (hasRole('TENANT_USER') and #tenantId == authentication.principal.tenantId)")
    public ResponseEntity<TenantResponse> getTenant(@PathVariable UUID tenantId) {
        log.info("Fetching tenant: {}", tenantId);
        TenantResponse response = tenantService.getTenant(tenantId);
        return ResponseEntity.ok(response);
    }

    /**
     * Get tenant by code
     */
    @GetMapping("/code/{tenantCode}")
    @Operation(summary = "Get tenant by code", description = "Retrieves tenant information by tenant code")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tenant found"),
            @ApiResponse(responseCode = "404", description = "Tenant not found")
    })
//    @PreAuthorize("hasRole('ADMIN') or hasRole('TENANT_ADMIN')")
    public ResponseEntity<TenantResponse> getTenantByCode(@PathVariable String tenantCode) {
        log.info("Fetching tenant by code: {}", tenantCode);
        TenantResponse response = tenantService.getTenantByCode(tenantCode);
        return ResponseEntity.ok(response);
    }

    /**
     * List all tenants
     */
    @GetMapping
    @Operation(summary = "List tenants", description = "Lists all tenants with pagination")
//    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<TenantSummaryResponse>> listTenants(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) TenantStatus status,
            @RequestParam(required = false) OrganizationType type) {

        log.info("Listing tenants - page: {}, size: {}", pageable.getPageNumber(), pageable.getPageSize());

        Page<TenantSummaryResponse> response;
        if (status != null) {
            response = tenantService.listTenantsByStatus(status, pageable);
        } else if (type != null) {
            response = tenantService.listTenantsByType(type, pageable);
        } else {
            response = tenantService.listTenants(pageable);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Activate a tenant
     */
    @PostMapping("/{tenantId}/activate")
    @Operation(summary = "Activate tenant", description = "Activates a pending or trial tenant")
//    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> activateTenant(
            @PathVariable UUID tenantId,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {

        log.info("Activating tenant: {}", tenantId);
        tenantService.activateTenant(tenantId, userId != null ? userId : "system");

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Tenant activated successfully"
        ));
    }

    /**
     * Suspend a tenant
     */
    @PostMapping("/{tenantId}/suspend")
    @Operation(summary = "Suspend tenant", description = "Suspends an active tenant")
//    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TenantResponse> suspendTenant(
            @PathVariable UUID tenantId,
            @RequestParam String reason,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {

        log.info("Suspending tenant: {}, reason: {}", tenantId, reason);
        TenantResponse response = tenantService.suspendTenant(tenantId, reason, userId != null ? userId : "system");
        return ResponseEntity.ok(response);
    }

    /**
     * Reactivate a suspended tenant
     */
    @PostMapping("/{tenantId}/reactivate")
    @Operation(summary = "Reactivate tenant", description = "Reactivates a suspended tenant")
//    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TenantResponse> reactivateTenant(
            @PathVariable UUID tenantId,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {

        log.info("Reactivating tenant: {}", tenantId);
        TenantResponse response = tenantService.reactivateTenant(tenantId, userId != null ? userId : "system");
        return ResponseEntity.ok(response);
    }

    /**
     * Delete a tenant
     */
    @DeleteMapping("/{tenantId}")
    @Operation(summary = "Delete tenant", description = "Soft deletes a tenant")
//    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> deleteTenant(
            @PathVariable UUID tenantId,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {

        log.info("Deleting tenant: {}", tenantId);
        tenantService.deleteTenant(tenantId, userId != null ? userId : "system");

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Tenant deleted successfully"
        ));
    }

    /**
     * Get tenant statistics
     */
    @GetMapping("/statistics")
    @Operation(summary = "Get tenant statistics", description = "Returns statistics about tenants")
//    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getTenantStatistics() {
        log.info("Fetching tenant statistics");
        Map<String, Object> stats = tenantService.getTenantStatistics();
        return ResponseEntity.ok(stats);
    }

    // ===== Subscription Endpoints =====

    /**
     * Get tenant subscription
     */
    @GetMapping("/{tenantId}/subscription")
    @Operation(summary = "Get tenant subscription", description = "Retrieves subscription details for a tenant")
//    @PreAuthorize("hasRole('ADMIN') or (hasRole('TENANT_ADMIN') and #tenantId == authentication.principal.tenantId)")
    public ResponseEntity<SubscriptionResponse> getTenantSubscription(@PathVariable UUID tenantId) {
        log.info("Fetching subscription for tenant: {}", tenantId);
        SubscriptionResponse response = subscriptionService.getSubscriptionByTenantId(tenantId);
        return ResponseEntity.ok(response);
    }

    /**
     * Update tenant subscription
     */
    @PutMapping("/{tenantId}/subscription")
    @Operation(summary = "Update subscription", description = "Updates tenant subscription plan")
//    @PreAuthorize("hasRole('ADMIN') or (hasRole('TENANT_ADMIN') and #tenantId == authentication.principal.tenantId)")
    public ResponseEntity<SubscriptionResponse> updateSubscription(
            @PathVariable UUID tenantId,
            @Valid @RequestBody UpdateSubscriptionRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {

        log.info("Updating subscription for tenant: {}", tenantId);
        SubscriptionResponse response = subscriptionService.updateSubscription(tenantId, request, userId);
        return ResponseEntity.ok(response);
    }

    // ===== Feature Flag Endpoints =====

    /**
     * Get tenant feature flags
     */
    @GetMapping("/{tenantId}/features")
    @Operation(summary = "Get feature flags", description = "Lists all feature flags for a tenant")
//    @PreAuthorize("hasRole('ADMIN') or (hasRole('TENANT_USER') and #tenantId == authentication.principal.tenantId)")
    public ResponseEntity<List<FeatureFlagResponse>> getTenantFeatures(@PathVariable UUID tenantId) {
        log.info("Fetching features for tenant: {}", tenantId);
        List<FeatureFlagResponse> features = featureFlagService.getTenantFeatures(tenantId);
        return ResponseEntity.ok(features);
    }

    /**
     * Update feature flag
     */
    @PutMapping("/{tenantId}/features/{featureCode}")
    @Operation(summary = "Update feature flag", description = "Enables or disables a feature for a tenant")
//    @PreAuthorize("hasRole('ADMIN') or (hasRole('TENANT_ADMIN') and #tenantId == authentication.principal.tenantId)")
    public ResponseEntity<FeatureFlagResponse> updateFeatureFlag(
            @PathVariable UUID tenantId,
            @PathVariable String featureCode,
            @RequestParam boolean enabled,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {

        log.info("Updating feature {} for tenant: {}, enabled: {}", featureCode, tenantId, enabled);
        FeatureFlagResponse response = featureFlagService.updateFeatureFlag(tenantId, featureCode, enabled, userId);
        return ResponseEntity.ok(response);
    }

    // ===== Settings Endpoints =====

    /**
     * Get tenant settings
     */
    @GetMapping("/{tenantId}/settings")
    @Operation(summary = "Get tenant settings", description = "Retrieves settings for a tenant")
//    @PreAuthorize("hasRole('ADMIN') or (hasRole('TENANT_ADMIN') and #tenantId == authentication.principal.tenantId)")
    public ResponseEntity<TenantSettingsResponse> getTenantSettings(@PathVariable UUID tenantId) {
        log.info("Fetching settings for tenant: {}", tenantId);
        TenantSettingsResponse response = settingsService.getTenantSettings(tenantId);
        return ResponseEntity.ok(response);
    }

    /**
     * Update tenant settings
     */
    @PutMapping("/{tenantId}/settings")
    @Operation(summary = "Update tenant settings", description = "Updates settings for a tenant")
//    @PreAuthorize("hasRole('ADMIN') or (hasRole('TENANT_ADMIN') and #tenantId == authentication.principal.tenantId)")
    public ResponseEntity<TenantSettingsResponse> updateTenantSettings(
            @PathVariable UUID tenantId,
            @Valid @RequestBody TenantSettingsRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {

        log.info("Updating settings for tenant: {}", tenantId);
        TenantSettingsResponse response = settingsService.updateTenantSettings(tenantId, request, userId);
        return ResponseEntity.ok(response);
    }
}