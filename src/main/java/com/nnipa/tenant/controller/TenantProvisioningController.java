package com.nnipa.tenant.controller;

import com.nnipa.tenant.dto.response.ApiResponse;
import com.nnipa.tenant.dto.response.TenantResponse;
import com.nnipa.tenant.entity.Tenant;
import com.nnipa.tenant.enums.TenantIsolationStrategy;
import com.nnipa.tenant.mapper.TenantMapper;
import com.nnipa.tenant.service.TenantProvisioningService;
import com.nnipa.tenant.service.TenantService;
import com.nnipa.tenant.util.ResponseUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for tenant provisioning operations.
 * Provides monitoring and management endpoints for provisioning.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/provisioning")
@RequiredArgsConstructor
@Tag(name = "Tenant Provisioning", description = "APIs for managing tenant provisioning")
public class TenantProvisioningController {

    private final TenantProvisioningService provisioningService;
    private final TenantService tenantService;
    private final TenantMapper tenantMapper;

    @GetMapping("/status")
    @Operation(summary = "Get provisioning status for a tenant")
    public ResponseEntity<ApiResponse<TenantProvisioningService.ProvisioningStatus>> getProvisioningStatus(
            @PathVariable UUID tenantId) {

        log.debug("Getting provisioning status for tenant: {}", tenantId);

        TenantProvisioningService.ProvisioningStatus status =
                provisioningService.getProvisioningStatus(tenantId);

        return ResponseUtil.success(status, "Provisioning status retrieved");
    }

    @PostMapping("/retry")
    @Operation(summary = "Retry failed provisioning")
    public ResponseEntity<ApiResponse<TenantResponse>> retryProvisioning(@PathVariable UUID tenantId) {

        log.info("Retrying provisioning for tenant: {}", tenantId);

        Tenant tenant = tenantService.retryProvisioning(tenantId);

        return ResponseUtil.success(
                tenantMapper.toResponse(tenant),
                "Provisioning retry initiated successfully"
        );
    }

    @PostMapping("/validate")
    @Operation(summary = "Validate provisioning for a tenant")
    public ResponseEntity<ApiResponse<ProvisioningValidationResponse>> validateProvisioning(
            @PathVariable UUID tenantId) {

        log.debug("Validating provisioning for tenant: {}", tenantId);

        Tenant tenant = tenantService.getTenantById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        boolean isValid = provisioningService.validateProvisioning(tenant);

        ProvisioningValidationResponse validationResponse = ProvisioningValidationResponse.builder()
                .tenantId(tenantId)
                .isValid(isValid)
                .isolationStrategy(tenant.getIsolationStrategy())
                .databaseName(tenant.getDatabaseName())
                .schemaName(tenant.getSchemaName())
                .build();

        String message = isValid ? "Provisioning validation successful" : "Provisioning validation failed";
        return ResponseUtil.success(validationResponse, message);
    }

    @DeleteMapping("/rollback")
    @Operation(summary = "Rollback provisioning and deprovision tenant resources")
    public ResponseEntity<ApiResponse<Void>> rollbackProvisioning(@PathVariable UUID tenantId) {

        log.warn("Rolling back provisioning for tenant: {}", tenantId);

        Tenant tenant = tenantService.getTenantById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        provisioningService.deprovisionTenant(tenant);

        return ResponseUtil.noContent("Provisioning rolled back successfully");
    }

    /**
     * Response DTOs
     */
    @Builder
    @Data
    public static class ProvisioningValidationResponse {
        private UUID tenantId;
        private boolean isValid;
        private TenantIsolationStrategy isolationStrategy;
        private String databaseName;
        private String schemaName;
    }
}