package com.nnipa.tenant.controller;

import com.nnipa.tenant.dto.response.ApiResponse;
import com.nnipa.tenant.dto.response.FeatureFlagResponse;
import com.nnipa.tenant.entity.FeatureFlag;
import com.nnipa.tenant.entity.Tenant;
import com.nnipa.tenant.mapper.FeatureFlagMapper;
import com.nnipa.tenant.service.FeatureFlagService;
import com.nnipa.tenant.service.TenantService;
import com.nnipa.tenant.util.ResponseUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for feature flag management.
 * Provides endpoints to manage feature flags for tenants.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/features")
@RequiredArgsConstructor
@Tag(name = "Feature Management", description = "APIs for managing feature flags")
public class FeatureFlagController {

    private final FeatureFlagService featureFlagService;
    private final TenantService tenantService;
    private final FeatureFlagMapper featureFlagMapper;

    @GetMapping("/tenant/{tenantId}")
    @Operation(summary = "Get tenant features")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Features retrieved successfully",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Tenant not found"
            )
    })
    public ResponseEntity<ApiResponse<List<FeatureFlagResponse>>> getTenantFeatures(
            @PathVariable UUID tenantId) {

        log.debug("Fetching features for tenant: {}", tenantId);

        Tenant tenant = tenantService.getTenantById(tenantId)
                .orElseThrow(() -> new RuntimeException(
                        String.format("Tenant with ID '%s' not found", tenantId)));

        List<FeatureFlag> features = featureFlagService.getTenantFeatures(tenant);
        List<FeatureFlagResponse> response = features.stream()
                .map(featureFlagMapper::toResponse)
                .collect(Collectors.toList());

        return ResponseUtil.success(response,
                String.format("Found %d features for tenant", response.size()));
    }

    @PostMapping("/tenant/{tenantId}/enable/{featureCode}")
    @Operation(summary = "Enable feature for tenant")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Feature enabled successfully",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Tenant or feature not found"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid configuration"
            )
    })
    public ResponseEntity<ApiResponse<FeatureFlagResponse>> enableFeature(
            @PathVariable UUID tenantId,
            @PathVariable String featureCode,
            @RequestBody(required = false) Map<String, Object> config) {

        log.info("Enabling feature {} for tenant: {}", featureCode, tenantId);

        Tenant tenant = tenantService.getTenantById(tenantId)
                .orElseThrow(() -> new RuntimeException(
                        String.format("Tenant with ID '%s' not found", tenantId)));

        FeatureFlag feature = featureFlagService.enableFeatureForTenant(tenant, featureCode);

        // Apply configuration if provided
        if (config != null && !config.isEmpty()) {
            log.debug("Applying configuration for feature {}: {}", featureCode, config);
            // You can extend this to save configuration with the feature flag
            // featureFlagService.updateFeatureConfig(feature, config);
        }

        return ResponseUtil.success(
                featureFlagMapper.toResponse(feature),
                String.format("Feature '%s' enabled successfully for tenant", featureCode)
        );
    }

    @PostMapping("/tenant/{tenantId}/disable/{featureCode}")
    @Operation(summary = "Disable feature for tenant")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "204",
                    description = "Feature disabled successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Tenant or feature not found"
            )
    })
    public ResponseEntity<ApiResponse<Void>> disableFeature(
            @PathVariable UUID tenantId,
            @PathVariable String featureCode) {

        log.info("Disabling feature {} for tenant: {}", featureCode, tenantId);

        Tenant tenant = tenantService.getTenantById(tenantId)
                .orElseThrow(() -> new RuntimeException(
                        String.format("Tenant with ID '%s' not found", tenantId)));

        featureFlagService.disableFeatureForTenant(tenant, featureCode);

        return ResponseUtil.noContent(
                String.format("Feature '%s' disabled successfully for tenant", featureCode)
        );
    }

    @GetMapping("/tenant/{tenantId}/check/{featureCode}")
    @Operation(summary = "Check if feature is enabled")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Feature status retrieved",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Tenant not found"
            )
    })
    public ResponseEntity<ApiResponse<FeatureStatusResponse>> checkFeature(
            @PathVariable UUID tenantId,
            @PathVariable String featureCode) {

        log.debug("Checking feature {} for tenant: {}", featureCode, tenantId);

        Tenant tenant = tenantService.getTenantById(tenantId)
                .orElseThrow(() -> new RuntimeException(
                        String.format("Tenant with ID '%s' not found", tenantId)));

        boolean enabled = featureFlagService.isFeatureEnabledForTenant(tenant, featureCode);

        FeatureStatusResponse status = FeatureStatusResponse.builder()
                .featureCode(featureCode)
                .enabled(enabled)
                .tenantId(tenantId)
                .tenantName(tenant.getName())
                .build();

        String message = enabled
                ? String.format("Feature '%s' is enabled for tenant", featureCode)
                : String.format("Feature '%s' is disabled for tenant", featureCode);

        return ResponseUtil.success(status, message);
    }

    @GetMapping("/me")
    @Operation(summary = "Get my features")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Features retrieved successfully",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Current tenant not found"
            )
    })
    public ResponseEntity<ApiResponse<List<FeatureFlagResponse>>> getMyFeatures(
            @RequestHeader("X-Tenant-ID") String tenantId) {

        log.debug("Fetching features for current tenant: {}", tenantId);

        try {
            UUID tenantUuid = UUID.fromString(tenantId);
            return getTenantFeatures(tenantUuid);
        } catch (IllegalArgumentException e) {
            log.error("Invalid tenant ID format: {}", tenantId);
            return ResponseUtil.notFound("Invalid tenant ID format");
        }
    }

    @GetMapping("/all")
    @Operation(summary = "Get all available features")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "All features retrieved successfully",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            )
    })
    public ResponseEntity<ApiResponse<List<FeatureFlagResponse>>> getAllFeatures() {
        log.debug("Fetching all available features");

        List<FeatureFlag> allFeatures = featureFlagService.getAllFeatures();
        List<FeatureFlagResponse> response = allFeatures.stream()
                .map(featureFlagMapper::toResponse)
                .collect(Collectors.toList());

        return ResponseUtil.success(response,
                String.format("Found %d available features", response.size()));
    }

    @PostMapping("/tenant/{tenantId}/bulk-enable")
    @Operation(summary = "Enable multiple features for tenant")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Features enabled successfully",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Tenant not found"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "207",
                    description = "Partial success - some features could not be enabled"
            )
    })
    public ResponseEntity<ApiResponse<BulkFeatureOperationResponse>> bulkEnableFeatures(
            @PathVariable UUID tenantId,
            @RequestBody List<String> featureCodes) {

        log.info("Bulk enabling {} features for tenant: {}", featureCodes.size(), tenantId);

        Tenant tenant = tenantService.getTenantById(tenantId)
                .orElseThrow(() -> new RuntimeException(
                        String.format("Tenant with ID '%s' not found", tenantId)));

        BulkFeatureOperationResponse result = BulkFeatureOperationResponse.builder()
                .tenantId(tenantId)
                .requestedFeatures(featureCodes)
                .successfulFeatures(new java.util.ArrayList<>())
                .failedFeatures(new java.util.ArrayList<>())
                .build();

        for (String featureCode : featureCodes) {
            try {
                featureFlagService.enableFeatureForTenant(tenant, featureCode);
                result.getSuccessfulFeatures().add(featureCode);
            } catch (Exception e) {
                log.error("Failed to enable feature {} for tenant {}: {}",
                        featureCode, tenantId, e.getMessage());
                result.getFailedFeatures().add(featureCode);
            }
        }

        String message = result.getFailedFeatures().isEmpty()
                ? String.format("All %d features enabled successfully", featureCodes.size())
                : String.format("%d features enabled, %d failed",
                result.getSuccessfulFeatures().size(),
                result.getFailedFeatures().size());

        return ResponseUtil.success(result, message);
    }

    /**
     * Response DTO for feature status check
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class FeatureStatusResponse {
        private String featureCode;
        private boolean enabled;
        private UUID tenantId;
        private String tenantName;
    }

    /**
     * Response DTO for bulk feature operations
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class BulkFeatureOperationResponse {
        private UUID tenantId;
        private List<String> requestedFeatures;
        private List<String> successfulFeatures;
        private List<String> failedFeatures;

        public int getTotalRequested() {
            return requestedFeatures != null ? requestedFeatures.size() : 0;
        }

        public int getTotalSuccessful() {
            return successfulFeatures != null ? successfulFeatures.size() : 0;
        }

        public int getTotalFailed() {
            return failedFeatures != null ? failedFeatures.size() : 0;
        }
    }
}