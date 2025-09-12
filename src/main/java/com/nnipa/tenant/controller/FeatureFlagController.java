package com.nnipa.tenant.controller;

import com.nnipa.tenant.dto.request.FeatureFlagRequest;
import com.nnipa.tenant.dto.response.FeatureFlagResponse;
import com.nnipa.tenant.enums.FeatureCategory;
import com.nnipa.tenant.service.FeatureFlagService;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for feature flag management
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/feature-flags")
@RequiredArgsConstructor
@Tag(name = "Feature Flag Management", description = "APIs for managing feature flags")
public class FeatureFlagController {

    private final FeatureFlagService featureFlagService;

    /**
     * Get all feature flags for a tenant
     */
    @GetMapping("/tenant/{tenantId}")
    @Operation(summary = "Get tenant feature flags", description = "Lists all feature flags for a tenant")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Feature flags retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Tenant not found")
    })
//    @PreAuthorize("hasRole('ADMIN') or (hasRole('TENANT_USER') and #tenantId == authentication.principal.tenantId)")
    public ResponseEntity<List<FeatureFlagResponse>> getTenantFeatureFlags(
            @PathVariable UUID tenantId,
            @RequestParam(required = false) Boolean enabledOnly,
            @RequestParam(required = false) FeatureCategory category) {

        log.info("Fetching feature flags for tenant: {}, enabledOnly: {}, category: {}",
                tenantId, enabledOnly, category);

        List<FeatureFlagResponse> features;

        if (Boolean.TRUE.equals(enabledOnly)) {
            features = featureFlagService.getEnabledFeatures(tenantId);
        } else {
            features = featureFlagService.getTenantFeatures(tenantId);
        }

        // Filter by category if specified
        if (category != null) {
            features = features.stream()
                    .filter(f -> category.equals(f.getCategory()))
                    .collect(Collectors.toList());
        }

        return ResponseEntity.ok(features);
    }

    /**
     * Get specific feature flag for a tenant
     */
    @GetMapping("/tenant/{tenantId}/feature/{featureCode}")
    @Operation(summary = "Get specific feature flag", description = "Retrieves a specific feature flag for a tenant")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Feature flag found"),
            @ApiResponse(responseCode = "404", description = "Feature flag not found")
    })
//    @PreAuthorize("hasRole('ADMIN') or (hasRole('TENANT_USER') and #tenantId == authentication.principal.tenantId)")
    public ResponseEntity<FeatureFlagResponse> getFeatureFlag(
            @PathVariable UUID tenantId,
            @PathVariable String featureCode) {

        log.info("Fetching feature flag {} for tenant: {}", featureCode, tenantId);

        List<FeatureFlagResponse> features = featureFlagService.getTenantFeatures(tenantId);

        return features.stream()
                .filter(f -> f.getFeatureCode().equals(featureCode))
                .findFirst()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Enable or disable a feature flag
     */
    @PutMapping("/tenant/{tenantId}/feature/{featureCode}/toggle")
    @Operation(summary = "Toggle feature flag", description = "Enables or disables a feature flag")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Feature flag toggled successfully"),
            @ApiResponse(responseCode = "404", description = "Feature flag not found"),
            @ApiResponse(responseCode = "400", description = "Feature cannot be enabled due to constraints")
    })
//    @PreAuthorize("hasRole('ADMIN') or (hasRole('TENANT_ADMIN') and #tenantId == authentication.principal.tenantId)")
    public ResponseEntity<FeatureFlagResponse> toggleFeatureFlag(
            @PathVariable UUID tenantId,
            @PathVariable String featureCode,
            @RequestParam boolean enabled,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {

        log.info("Toggling feature {} for tenant: {}, enabled: {}", featureCode, tenantId, enabled);

        FeatureFlagResponse response = featureFlagService.updateFeatureFlag(
                tenantId, featureCode, enabled, userId != null ? userId : "system");

        return ResponseEntity.ok(response);
    }

    /**
     * Create or update feature flag
     */
    @PutMapping("/tenant/{tenantId}/feature/{featureCode}")
    @Operation(summary = "Create or update feature flag", description = "Creates a new feature flag or updates existing one")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Feature flag updated successfully"),
            @ApiResponse(responseCode = "201", description = "Feature flag created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request data")
    })
//    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<FeatureFlagResponse> createOrUpdateFeatureFlag(
            @PathVariable UUID tenantId,
            @PathVariable String featureCode,
            @Valid @RequestBody FeatureFlagRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {

        log.info("Creating/updating feature {} for tenant: {}", featureCode, tenantId);

        // Ensure feature code matches
        request.setFeatureCode(featureCode);

        FeatureFlagResponse response = featureFlagService.createOrUpdateFeatureFlag(
                tenantId, request, userId != null ? userId : "system");

        return ResponseEntity.ok(response);
    }

    /**
     * Batch update feature flags
     */
    @PutMapping("/tenant/{tenantId}/features/batch")
    @Operation(summary = "Batch update features", description = "Updates multiple feature flags at once")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Features updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request data")
    })
//    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> batchUpdateFeatureFlags(
            @PathVariable UUID tenantId,
            @RequestBody Map<String, Boolean> features,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {

        log.info("Batch updating {} features for tenant: {}", features.size(), tenantId);

        Map<String, Object> results = new HashMap<>();
        int successCount = 0;
        int failureCount = 0;

        for (Map.Entry<String, Boolean> entry : features.entrySet()) {
            try {
                featureFlagService.updateFeatureFlag(
                        tenantId, entry.getKey(), entry.getValue(), userId != null ? userId : "system");
                successCount++;
            } catch (Exception e) {
                log.error("Failed to update feature {}: {}", entry.getKey(), e.getMessage());
                failureCount++;
            }
        }

        results.put("success", successCount);
        results.put("failed", failureCount);
        results.put("total", features.size());

        return ResponseEntity.ok(results);
    }

    /**
     * Check if feature is enabled
     */
    @GetMapping("/tenant/{tenantId}/feature/{featureCode}/enabled")
    @Operation(summary = "Check feature status", description = "Checks if a feature is enabled for a tenant")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Feature status retrieved")
    })
//    @PreAuthorize("hasRole('ADMIN') or (hasRole('TENANT_USER') and #tenantId == authentication.principal.tenantId)")
    public ResponseEntity<Map<String, Object>> isFeatureEnabled(
            @PathVariable UUID tenantId,
            @PathVariable String featureCode) {

        log.debug("Checking if feature {} is enabled for tenant: {}", featureCode, tenantId);

        boolean enabled = featureFlagService.isFeatureEnabled(tenantId, featureCode);

        Map<String, Object> response = new HashMap<>();
        response.put("tenantId", tenantId);
        response.put("featureCode", featureCode);
        response.put("enabled", enabled);

        return ResponseEntity.ok(response);
    }

    /**
     * Increment feature usage
     */
    @PostMapping("/tenant/{tenantId}/feature/{featureCode}/usage")
    @Operation(summary = "Increment usage", description = "Increments the usage counter for a feature")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usage incremented successfully"),
            @ApiResponse(responseCode = "404", description = "Feature not found"),
            @ApiResponse(responseCode = "429", description = "Usage limit exceeded")
    })
//    @PreAuthorize("hasRole('ADMIN') or (hasRole('TENANT_USER') and #tenantId == authentication.principal.tenantId)")
    public ResponseEntity<Map<String, String>> incrementFeatureUsage(
            @PathVariable UUID tenantId,
            @PathVariable String featureCode) {

        log.debug("Incrementing usage for feature {} in tenant: {}", featureCode, tenantId);

        featureFlagService.incrementFeatureUsage(tenantId, featureCode);

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Usage incremented successfully"
        ));
    }

    /**
     * Get all available feature codes
     */
    @GetMapping("/available-features")
    @Operation(summary = "Get available features", description = "Lists all available feature codes in the system")
//    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<String>> getAvailableFeatures() {
        log.info("Fetching all available feature codes");

        // This would return all possible feature codes
        List<String> features = List.of(
                "BASIC_ANALYTICS",
                "ADVANCED_ANALYTICS",
                "STANDARD_REPORTS",
                "CUSTOM_REPORTS",
                "EMAIL_NOTIFICATIONS",
                "SMS_NOTIFICATIONS",
                "WEBHOOK_INTEGRATION",
                "API_ACCESS",
                "DATA_EXPORT",
                "DATA_IMPORT",
                "CUSTOM_DASHBOARDS",
                "ML_MODELS",
                "PRIORITY_SUPPORT",
                "SSO_INTEGRATION",
                "AUDIT_LOGS",
                "COMPLIANCE_REPORTS",
                "MULTI_FACTOR_AUTH",
                "IP_WHITELISTING",
                "CUSTOM_BRANDING",
                "ADVANCED_SECURITY"
        );

        return ResponseEntity.ok(features);
    }

    /**
     * Get feature usage statistics
     */
    @GetMapping("/tenant/{tenantId}/usage-statistics")
    @Operation(summary = "Get usage statistics", description = "Returns usage statistics for all features")
//    @PreAuthorize("hasRole('ADMIN') or (hasRole('TENANT_ADMIN') and #tenantId == authentication.principal.tenantId)")
    public ResponseEntity<Map<String, Object>> getFeatureUsageStatistics(
            @PathVariable UUID tenantId) {

        log.info("Fetching feature usage statistics for tenant: {}", tenantId);

        List<FeatureFlagResponse> features = featureFlagService.getTenantFeatures(tenantId);

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalFeatures", features.size());
        stats.put("enabledFeatures", features.stream().filter(FeatureFlagResponse::getIsEnabled).count());
        stats.put("betaFeatures", features.stream().filter(FeatureFlagResponse::getIsBeta).count());
        stats.put("experimentalFeatures", features.stream().filter(FeatureFlagResponse::getIsExperimental).count());

        // Add usage details for features with limits
        List<Map<String, Object>> usageDetails = features.stream()
                .filter(f -> f.getUsageLimit() != null)
                .map(f -> {
                    Map<String, Object> detail = new HashMap<>();
                    detail.put("featureCode", f.getFeatureCode());
                    detail.put("currentUsage", f.getCurrentUsage());
                    detail.put("usageLimit", f.getUsageLimit());
                    detail.put("percentageUsed",
                            f.getCurrentUsage() * 100.0 / f.getUsageLimit());
                    return detail;
                })
                .collect(Collectors.toList());

        stats.put("usageDetails", usageDetails);

        return ResponseEntity.ok(stats);
    }
}