package com.nnipa.tenant.controller;

import com.nnipa.tenant.dto.request.FeatureFlagRequest;
import com.nnipa.tenant.dto.response.FeatureFlagResponse;
import com.nnipa.tenant.entity.FeatureFlag;
import com.nnipa.tenant.entity.Tenant;
import com.nnipa.tenant.mapper.FeatureFlagMapper;
import com.nnipa.tenant.service.FeatureFlagService;
import com.nnipa.tenant.service.TenantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST API controller for feature flag management.
 * Controls feature access and A/B testing for tenants.
 */
@Slf4j
@RestController
@RequestMapping("/features")
@RequiredArgsConstructor
@Tag(name = "Feature Management", description = "APIs for managing feature flags and access control")
public class FeatureFlagController {

    private final FeatureFlagService featureFlagService;
    private final TenantService tenantService;
    private final FeatureFlagMapper featureFlagMapper;

    /**
     * Gets all features for a tenant.
     */
    @GetMapping("/tenant/{tenantId}")
    @PreAuthorize("hasRole('ADMIN') or @tenantSecurity.canAccessTenant(#tenantId)")
    @Operation(summary = "Get tenant features",
            description = "Lists all feature flags for a specific tenant")
    public ResponseEntity<List<FeatureFlagResponse>> getTenantFeatures(@PathVariable UUID tenantId) {
        log.debug("Fetching features for tenant: {}", tenantId);

        Tenant tenant = tenantService.getTenantById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        List<FeatureFlag> features = featureFlagService.getTenantFeatures(tenant);
        List<FeatureFlagResponse> response = features.stream()
                .map(featureFlagMapper::toResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /**
     * Gets enabled features for a tenant.
     */
    @GetMapping("/tenant/{tenantId}/enabled")
    @PreAuthorize("hasRole('ADMIN') or @tenantSecurity.canAccessTenant(#tenantId)")
    @Operation(summary = "Get enabled features",
            description = "Lists only enabled features for a tenant")
    public ResponseEntity<List<FeatureFlagResponse>> getEnabledFeatures(@PathVariable UUID tenantId) {
        log.debug("Fetching enabled features for tenant: {}", tenantId);

        Tenant tenant = tenantService.getTenantById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        List<FeatureFlag> features = featureFlagService.getEnabledFeatures(tenant);
        List<FeatureFlagResponse> response = features.stream()
                .map(featureFlagMapper::toResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /**
     * Checks if a feature is enabled for a tenant.
     */
    @GetMapping("/tenant/{tenantId}/check/{featureCode}")
    @Operation(summary = "Check feature status",
            description = "Checks if a specific feature is enabled for a tenant")
    public ResponseEntity<Map<String, Object>> checkFeature(
            @PathVariable UUID tenantId,
            @PathVariable String featureCode) {

        log.debug("Checking feature {} for tenant: {}", featureCode, tenantId);

        Tenant tenant = tenantService.getTenantById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        boolean enabled = featureFlagService.isFeatureEnabled(tenant, featureCode);

        return ResponseEntity.ok(Map.of(
                "tenantId", tenantId,
                "featureCode", featureCode,
                "enabled", enabled
        ));
    }

    /**
     * Enables a feature for a tenant.
     */
    @PostMapping("/tenant/{tenantId}/enable/{featureCode}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('FEATURE_MANAGER')")
    @Operation(summary = "Enable feature",
            description = "Enables a specific feature for a tenant")
    public ResponseEntity<FeatureFlagResponse> enableFeature(
            @PathVariable UUID tenantId,
            @PathVariable String featureCode) {

        log.info("Enabling feature {} for tenant: {}", featureCode, tenantId);

        Tenant tenant = tenantService.getTenantById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        FeatureFlag feature = featureFlagService.enableFeature(tenant, featureCode);
        return ResponseEntity.ok(featureFlagMapper.toResponse(feature));
    }

    /**
     * Disables a feature for a tenant.
     */
    @PostMapping("/tenant/{tenantId}/disable/{featureCode}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('FEATURE_MANAGER')")
    @Operation(summary = "Disable feature",
            description = "Disables a specific feature for a tenant")
    public ResponseEntity<FeatureFlagResponse> disableFeature(
            @PathVariable UUID tenantId,
            @PathVariable String featureCode) {

        log.info("Disabling feature {} for tenant: {}", featureCode, tenantId);

        Tenant tenant = tenantService.getTenantById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        FeatureFlag feature = featureFlagService.disableFeature(tenant, featureCode);
        return ResponseEntity.ok(featureFlagMapper.toResponse(feature));
    }

    /**
     * Grants trial access to a feature.
     */
    @PostMapping("/tenant/{tenantId}/trial/{featureCode}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SALES_MANAGER')")
    @Operation(summary = "Grant trial access",
            description = "Grants temporary trial access to a premium feature")
    public ResponseEntity<FeatureFlagResponse> grantTrialAccess(
            @PathVariable UUID tenantId,
            @PathVariable String featureCode,
            @RequestParam(defaultValue = "30") int trialDays) {

        log.info("Granting {} day trial for feature {} to tenant: {}",
                trialDays, featureCode, tenantId);

        Tenant tenant = tenantService.getTenantById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        FeatureFlag feature = featureFlagService.grantTrialAccess(tenant, featureCode, trialDays);
        return ResponseEntity.ok(featureFlagMapper.toResponse(feature));
    }

    /**
     * Approves a feature requiring approval.
     */
    @PostMapping("/tenant/{tenantId}/approve/{featureCode}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMPLIANCE_OFFICER')")
    @Operation(summary = "Approve feature",
            description = "Approves a feature that requires manual approval")
    public ResponseEntity<FeatureFlagResponse> approveFeature(
            @PathVariable UUID tenantId,
            @PathVariable String featureCode,
            @RequestParam String approvedBy,
            @RequestParam(required = false) String notes) {

        log.info("Approving feature {} for tenant: {} by {}",
                featureCode, tenantId, approvedBy);

        Tenant tenant = tenantService.getTenantById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        FeatureFlag feature = featureFlagService.approveFeature(tenant, featureCode, approvedBy, notes);
        return ResponseEntity.ok(featureFlagMapper.toResponse(feature));
    }

    /**
     * Sets A/B test group for a feature.
     */
    @PostMapping("/tenant/{tenantId}/ab-test/{featureCode}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PRODUCT_MANAGER')")
    @Operation(summary = "Set A/B test group",
            description = "Assigns tenant to an A/B test group for a feature")
    public ResponseEntity<FeatureFlagResponse> setABTestGroup(
            @PathVariable UUID tenantId,
            @PathVariable String featureCode,
            @RequestParam String group,
            @RequestParam(defaultValue = "50") int rolloutPercentage) {

        log.info("Setting A/B test group {} for feature {} ({}% rollout)",
                group, featureCode, rolloutPercentage);

        Tenant tenant = tenantService.getTenantById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        FeatureFlag feature = featureFlagService.setABTestGroup(
                tenant, featureCode, group, rolloutPercentage);

        return ResponseEntity.ok(featureFlagMapper.toResponse(feature));
    }

    /**
     * Records feature usage.
     */
    @PostMapping("/usage/{tenantId}/{featureCode}")
    @Operation(summary = "Record feature usage",
            description = "Records that a feature has been used")
    public ResponseEntity<Void> recordUsage(
            @PathVariable UUID tenantId,
            @PathVariable String featureCode) {

        log.debug("Recording usage of feature {} by tenant: {}", featureCode, tenantId);

        Tenant tenant = tenantService.getTenantById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        featureFlagService.recordFeatureUsage(tenant, featureCode);
        return ResponseEntity.accepted().build();
    }

    /**
     * Bulk updates features for a tenant.
     */
    @PutMapping("/tenant/{tenantId}/bulk")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Bulk update features",
            description = "Updates multiple features at once for a tenant")
    public ResponseEntity<List<FeatureFlagResponse>> bulkUpdateFeatures(
            @PathVariable UUID tenantId,
            @Valid @RequestBody Map<String, Boolean> featureStates) {

        log.info("Bulk updating {} features for tenant: {}",
                featureStates.size(), tenantId);

        Tenant tenant = tenantService.getTenantById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        List<FeatureFlag> updatedFeatures = featureStates.entrySet().stream()
                .map(entry -> {
                    if (entry.getValue()) {
                        return featureFlagService.enableFeature(tenant, entry.getKey());
                    } else {
                        return featureFlagService.disableFeature(tenant, entry.getKey());
                    }
                })
                .collect(Collectors.toList());

        List<FeatureFlagResponse> response = updatedFeatures.stream()
                .map(featureFlagMapper::toResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /**
     * Gets feature catalog.
     */
    @GetMapping("/catalog")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PRODUCT_MANAGER')")
    @Operation(summary = "Get feature catalog",
            description = "Lists all available features in the system")
    public ResponseEntity<List<Map<String, Object>>> getFeatureCatalog() {
        log.debug("Fetching feature catalog");

        // Implementation would return feature definitions
        return ResponseEntity.ok().build();
    }

    /**
     * Self-service endpoint for current tenant's features.
     */
    @GetMapping("/me")
    @Operation(summary = "Get my features",
            description = "Lists features for the authenticated tenant")
    public ResponseEntity<List<FeatureFlagResponse>> getMyFeatures(
            @RequestHeader("X-Tenant-ID") String tenantId) {

        log.debug("Fetching features for current tenant: {}", tenantId);

        Tenant tenant = tenantService.getTenantById(UUID.fromString(tenantId))
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        List<FeatureFlag> features = featureFlagService.getTenantFeatures(tenant);
        List<FeatureFlagResponse> response = features.stream()
                .map(featureFlagMapper::toResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /**
     * Self-service feature check.
     */
    @GetMapping("/me/check/{featureCode}")
    @Operation(summary = "Check my feature",
            description = "Checks if a feature is enabled for the authenticated tenant")
    public ResponseEntity<Map<String, Object>> checkMyFeature(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable String featureCode) {

        log.debug("Checking feature {} for current tenant: {}", featureCode, tenantId);

        Tenant tenant = tenantService.getTenantById(UUID.fromString(tenantId))
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        boolean enabled = featureFlagService.isFeatureEnabled(tenant, featureCode);

        return ResponseEntity.ok(Map.of(
                "featureCode", featureCode,
                "enabled", enabled,
                "tenantId", tenantId
        ));
    }
}