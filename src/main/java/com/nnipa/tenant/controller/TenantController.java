package com.nnipa.tenant.controller;

import com.nnipa.tenant.dto.request.CreateTenantRequest;
import com.nnipa.tenant.dto.request.UpdateTenantRequest;
import com.nnipa.tenant.dto.response.TenantResponse;
import com.nnipa.tenant.dto.response.TenantStatisticsResponse;
import com.nnipa.tenant.entity.Tenant;
import com.nnipa.tenant.enums.OrganizationType;
import com.nnipa.tenant.mapper.TenantMapper;
import com.nnipa.tenant.service.OrganizationHierarchyService;
import com.nnipa.tenant.service.TenantService;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for tenant management operations.
 * Authorization handled by authz-service through API Gateway.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/tenants")
@RequiredArgsConstructor
@Tag(name = "Tenant Management", description = "APIs for managing tenants")
public class TenantController {

    private final TenantService tenantService;
    private final OrganizationHierarchyService hierarchyService;
    private final TenantMapper tenantMapper;

    @PostMapping
    @Operation(summary = "Create a new tenant")
    public ResponseEntity<TenantResponse> createTenant(
            @Valid @RequestBody CreateTenantRequest request) {

        log.info("Creating tenant: {} ({})", request.getName(), request.getOrganizationType());

        Tenant tenant = tenantMapper.toEntity(request);
        tenant = tenantService.createTenant(tenant);
        TenantResponse response = tenantMapper.toResponse(tenant);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get tenant by ID")
    public ResponseEntity<TenantResponse> getTenant(@PathVariable UUID id) {
        log.debug("Fetching tenant: {}", id);

        return tenantService.getTenantById(id)
                .map(tenantMapper::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/code/{code}")
    @Operation(summary = "Get tenant by code")
    public ResponseEntity<TenantResponse> getTenantByCode(@PathVariable String code) {
        log.debug("Fetching tenant by code: {}", code);

        return tenantService.getTenantByCode(code)
                .map(tenantMapper::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update tenant")
    public ResponseEntity<TenantResponse> updateTenant(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTenantRequest request) {

        log.info("Updating tenant: {}", id);

        Tenant updates = tenantMapper.toEntity(request);
        Tenant tenant = tenantService.updateTenant(id, updates);

        return ResponseEntity.ok(tenantMapper.toResponse(tenant));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete tenant")
    public ResponseEntity<Void> deleteTenant(@PathVariable UUID id) {
        log.warn("Marking tenant for deletion: {}", id);

        tenantService.markForDeletion(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/activate")
    @Operation(summary = "Activate tenant")
    public ResponseEntity<TenantResponse> activateTenant(@PathVariable UUID id) {
        log.info("Activating tenant: {}", id);

        Tenant tenant = tenantService.activateTenant(id);
        return ResponseEntity.ok(tenantMapper.toResponse(tenant));
    }

    @PostMapping("/{id}/suspend")
    @Operation(summary = "Suspend tenant")
    public ResponseEntity<TenantResponse> suspendTenant(
            @PathVariable UUID id,
            @RequestParam String reason) {

        log.warn("Suspending tenant: {} (Reason: {})", id, reason);

        Tenant tenant = tenantService.suspendTenant(id, reason);
        return ResponseEntity.ok(tenantMapper.toResponse(tenant));
    }

    @PostMapping("/{id}/verify")
    @Operation(summary = "Verify tenant")
    public ResponseEntity<TenantResponse> verifyTenant(
            @PathVariable UUID id,
            @RequestParam String verifiedBy,
            @RequestParam(required = false) String verificationDocument) {

        log.info("Verifying tenant: {} by {}", id, verifiedBy);

        Tenant tenant = tenantService.verifyTenant(id, verifiedBy, verificationDocument);
        return ResponseEntity.ok(tenantMapper.toResponse(tenant));
    }

    @GetMapping
    @Operation(summary = "List tenants")
    public ResponseEntity<Page<TenantResponse>> listTenants(
            @RequestParam(required = false) OrganizationType organizationType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        log.debug("Listing tenants - Type: {}, Status: {}, Search: {}", organizationType, status, search);

        Page<Tenant> tenants = tenantService.searchTenants(search, pageable);
        Page<TenantResponse> response = tenants.map(tenantMapper::toResponse);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    @Operation(summary = "Get current tenant")
    public ResponseEntity<TenantResponse> getCurrentTenant(
            @RequestHeader("X-Tenant-ID") String tenantId) {

        log.debug("Fetching current tenant: {}", tenantId);

        return tenantService.getTenantById(UUID.fromString(tenantId))
                .map(tenantMapper::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/statistics")
    @Operation(summary = "Get tenant statistics")
    public ResponseEntity<TenantStatisticsResponse> getTenantStatistics() {
        log.debug("Fetching tenant statistics");

        TenantService.TenantStatistics stats = tenantService.getTenantStatistics();
        TenantStatisticsResponse response = TenantStatisticsResponse.builder()
                .totalTenants(stats.getTotalTenants())
                .activeTenants(stats.getActiveTenants())
                .statisticsByType(stats.getStatisticsByType())
                .build();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/children")
    @Operation(summary = "Get child tenants")
    public ResponseEntity<List<TenantResponse>> getChildTenants(@PathVariable UUID id) {
        log.debug("Fetching children for tenant: {}", id);

        List<Tenant> children = hierarchyService.getChildren(id);
        List<TenantResponse> response = children.stream()
                .map(tenantMapper::toResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }
}