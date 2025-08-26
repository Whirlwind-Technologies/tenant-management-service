package com.nnipa.tenant.controller;

import com.nnipa.tenant.entity.Tenant;
import com.nnipa.tenant.enums.TenantIsolationStrategy;
import com.nnipa.tenant.enums.TenantStatus;
import com.nnipa.tenant.service.TenantProvisioningService;
import com.nnipa.tenant.service.TenantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

    @GetMapping("/status")
    @Operation(summary = "Get provisioning status for a tenant")
    public ResponseEntity<TenantProvisioningService.ProvisioningStatus> getProvisioningStatus(
            @PathVariable UUID tenantId) {

        log.debug("Getting provisioning status for tenant: {}", tenantId);

        TenantProvisioningService.ProvisioningStatus status =
                provisioningService.getProvisioningStatus(tenantId);

        return ResponseEntity.ok(status);
    }

    @PostMapping("/retry")
    @Operation(summary = "Retry failed provisioning")
    public ResponseEntity<TenantResponse> retryProvisioning(@PathVariable UUID tenantId) {

        log.info("Retrying provisioning for tenant: {}", tenantId);

        Tenant tenant = tenantService.retryProvisioning(tenantId);

        // Convert to response DTO (assuming TenantResponse exists)
        TenantResponse response = TenantResponse.builder()
                .id(tenant.getId())
                .tenantCode(tenant.getTenantCode())
                .name(tenant.getName())
                .status(tenant.getStatus())
                .isolationStrategy(tenant.getIsolationStrategy())
                .build();

        return ResponseEntity.ok(response);
    }

    @PostMapping("/validate")
    @Operation(summary = "Validate provisioning for a tenant")
    public ResponseEntity<ProvisioningValidationResponse> validateProvisioning(
            @PathVariable UUID tenantId) {

        log.debug("Validating provisioning for tenant: {}", tenantId);

        Tenant tenant = tenantService.getTenantById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        boolean isValid = provisioningService.validateProvisioning(tenant);

        return ResponseEntity.ok(ProvisioningValidationResponse.builder()
                .tenantId(tenantId)
                .isValid(isValid)
                .isolationStrategy(tenant.getIsolationStrategy())
                .databaseName(tenant.getDatabaseName())
                .schemaName(tenant.getSchemaName())
                .build());
    }

    @DeleteMapping("/rollback")
    @Operation(summary = "Rollback provisioning and deprovision tenant resources")
    public ResponseEntity<Void> rollbackProvisioning(@PathVariable UUID tenantId) {

        log.warn("Rolling back provisioning for tenant: {}", tenantId);

        Tenant tenant = tenantService.getTenantById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        provisioningService.deprovisionTenant(tenant);

        return ResponseEntity.noContent().build();
    }

    /**
     * Response DTOs
     */
    @lombok.Builder
    @lombok.Data
    public static class TenantResponse {
        private UUID id;
        private String tenantCode;
        private String name;
        private TenantStatus status;
        private TenantIsolationStrategy isolationStrategy;
    }

    @lombok.Builder
    @lombok.Data
    public static class ProvisioningValidationResponse {
        private UUID tenantId;
        private boolean isValid;
        private TenantIsolationStrategy isolationStrategy;
        private String databaseName;
        private String schemaName;
    }
}