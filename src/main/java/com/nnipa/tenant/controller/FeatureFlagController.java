package com.nnipa.tenant.controller;

import com.nnipa.tenant.dto.response.FeatureFlagResponse;
import com.nnipa.tenant.entity.FeatureFlag;
import com.nnipa.tenant.entity.Tenant;
import com.nnipa.tenant.mapper.FeatureFlagMapper;
import com.nnipa.tenant.service.FeatureFlagService;
import com.nnipa.tenant.service.TenantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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

    @PostMapping("/tenant/{tenantId}/enable/{featureCode}")
    @Operation(summary = "Enable feature for tenant")
    public ResponseEntity<FeatureFlagResponse> enableFeature(
            @PathVariable UUID tenantId,
            @PathVariable String featureCode,
            @RequestBody(required = false) Map<String, Object> config) {

        log.info("Enabling feature {} for tenant: {}", featureCode, tenantId);

        Tenant tenant = tenantService.getTenantById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        FeatureFlag feature = featureFlagService.enableFeatureForTenant(tenant, featureCode);
        return ResponseEntity.ok(featureFlagMapper.toResponse(feature));
    }

    @PostMapping("/tenant/{tenantId}/disable/{featureCode}")
    @Operation(summary = "Disable feature for tenant")
    public ResponseEntity<Void> disableFeature(
            @PathVariable UUID tenantId,
            @PathVariable String featureCode) {

        log.info("Disabling feature {} for tenant: {}", featureCode, tenantId);

        Tenant tenant = tenantService.getTenantById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        featureFlagService.disableFeatureForTenant(tenant, featureCode);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/tenant/{tenantId}/check/{featureCode}")
    @Operation(summary = "Check if feature is enabled")
    public ResponseEntity<Map<String, Object>> checkFeature(
            @PathVariable UUID tenantId,
            @PathVariable String featureCode) {

        log.debug("Checking feature {} for tenant: {}", featureCode, tenantId);

        Tenant tenant = tenantService.getTenantById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        boolean enabled = featureFlagService.isFeatureEnabledForTenant(tenant, featureCode);

        return ResponseEntity.ok(Map.of(
                "featureCode", featureCode,
                "enabled", enabled,
                "tenantId", tenantId
        ));
    }

    @GetMapping("/me")
    @Operation(summary = "Get my features")
    public ResponseEntity<List<FeatureFlagResponse>> getMyFeatures(
            @RequestHeader("X-Tenant-ID") String tenantId) {

        log.debug("Fetching features for current tenant: {}", tenantId);

        return getTenantFeatures(UUID.fromString(tenantId));
    }
}