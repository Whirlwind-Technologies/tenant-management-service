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
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST API controller for tenant management operations.
 * Provides endpoints for CRUD operations and tenant lifecycle management.
 */
@Slf4j
@RestController
@RequestMapping("/tenants")
@RequiredArgsConstructor
@Tag(name = "Tenant Management", description = "APIs for managing tenants and organizations")
@SecurityRequirement(name = "bearerAuth")
public class TenantController {

    private final TenantService tenantService;
    private final OrganizationHierarchyService hierarchyService;
    private final TenantMapper tenantMapper;

    /**
     * Creates a new tenant.
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('TENANT_MANAGER')")
    @Operation(summary = "Create a new tenant",
            description = "Creates a new tenant with automatic organization classification and resource provisioning")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Tenant created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "409", description = "Tenant already exists")
    })
    public ResponseEntity<TenantResponse> createTenant(
            @Valid @RequestBody CreateTenantRequest request) {

        log.info("Creating tenant: {} ({})", request.getName(), request.getOrganizationType());

        Tenant tenant = tenantMapper.toEntity(request);
        tenant = tenantService.createTenant(tenant);
        TenantResponse response = tenantMapper.toResponse(tenant);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Gets a tenant by ID.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @tenantSecurity.canAccessTenant(#id)")
    @Operation(summary = "Get tenant by ID",
            description = "Retrieves detailed information about a specific tenant")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tenant found"),
            @ApiResponse(responseCode = "404", description = "Tenant not found"),
            @ApiResponse(responseCode = "403", description = "Access denied")
    })
    public ResponseEntity<TenantResponse> getTenant(
            @Parameter(description = "Tenant ID") @PathVariable UUID id) {

        log.debug("Fetching tenant: {}", id);

        return tenantService.getTenantById(id)
                .map(tenantMapper::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Gets a tenant by code.
     */
    @GetMapping("/code/{code}")
    @PreAuthorize("hasRole('ADMIN') or @tenantSecurity.canAccessTenantByCode(#code)")
    @Operation(summary = "Get tenant by code",
            description = "Retrieves tenant information using tenant code")
    public ResponseEntity<TenantResponse> getTenantByCode(
            @Parameter(description = "Tenant code") @PathVariable String code) {

        log.debug("Fetching tenant by code: {}", code);

        return tenantService.getTenantByCode(code)
                .map(tenantMapper::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Updates a tenant.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @tenantSecurity.isTenantAdmin(#id)")
    @Operation(summary = "Update tenant",
            description = "Updates tenant information and settings")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tenant updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "404", description = "Tenant not found")
    })
    public ResponseEntity<TenantResponse> updateTenant(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTenantRequest request) {

        log.info("Updating tenant: {}", id);

        Tenant updates = tenantMapper.toEntity(request);
        Tenant tenant = tenantService.updateTenant(id, updates);
        TenantResponse response = tenantMapper.toResponse(tenant);

        return ResponseEntity.ok(response);
    }

    /**
     * Activates a tenant.
     */
    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Activate tenant",
            description = "Activates a pending or suspended tenant")
    public ResponseEntity<TenantResponse> activateTenant(@PathVariable UUID id) {
        log.info("Activating tenant: {}", id);

        Tenant tenant = tenantService.activateTenant(id);
        return ResponseEntity.ok(tenantMapper.toResponse(tenant));
    }

    /**
     * Suspends a tenant.
     */
    @PostMapping("/{id}/suspend")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Suspend tenant",
            description = "Temporarily suspends a tenant's access")
    public ResponseEntity<TenantResponse> suspendTenant(
            @PathVariable UUID id,
            @RequestParam String reason) {

        log.info("Suspending tenant: {} (Reason: {})", id, reason);

        Tenant tenant = tenantService.suspendTenant(id, reason);
        return ResponseEntity.ok(tenantMapper.toResponse(tenant));
    }

    /**
     * Verifies a tenant.
     */
    @PostMapping("/{id}/verify")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMPLIANCE_OFFICER')")
    @Operation(summary = "Verify tenant",
            description = "Marks a tenant as verified after document validation")
    public ResponseEntity<TenantResponse> verifyTenant(
            @PathVariable UUID id,
            @RequestParam String verifiedBy,
            @RequestParam(required = false) String verificationDocument) {

        log.info("Verifying tenant: {} by {}", id, verifiedBy);

        Tenant tenant = tenantService.verifyTenant(id, verifiedBy, verificationDocument);
        return ResponseEntity.ok(tenantMapper.toResponse(tenant));
    }

    /**
     * Marks a tenant for deletion.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete tenant",
            description = "Marks a tenant for deletion (soft delete with grace period)")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Tenant marked for deletion"),
            @ApiResponse(responseCode = "404", description = "Tenant not found")
    })
    public ResponseEntity<Void> deleteTenant(@PathVariable UUID id) {
        log.info("Marking tenant for deletion: {}", id);

        tenantService.markForDeletion(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Lists all tenants with pagination.
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('TENANT_VIEWER')")
    @Operation(summary = "List tenants",
            description = "Lists all tenants with pagination and filtering")
    public ResponseEntity<Page<TenantResponse>> listTenants(
            @Parameter(description = "Organization type filter")
            @RequestParam(required = false) OrganizationType organizationType,
            @Parameter(description = "Status filter")
            @RequestParam(required = false) String status,
            @Parameter(description = "Search query")
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        log.debug("Listing tenants - Type: {}, Status: {}, Search: {}",
                organizationType, status, search);

        Page<Tenant> tenants = tenantService.searchTenants(search, pageable);
        Page<TenantResponse> response = tenants.map(tenantMapper::toResponse);

        return ResponseEntity.ok(response);
    }

    /**
     * Gets active tenants.
     */
    @GetMapping("/active")
    @PreAuthorize("hasRole('ADMIN') or hasRole('TENANT_VIEWER')")
    @Operation(summary = "Get active tenants",
            description = "Lists all active and trial tenants")
    public ResponseEntity<List<TenantResponse>> getActiveTenants() {
        log.debug("Fetching active tenants");

        List<Tenant> tenants = tenantService.getActiveTenants();
        List<TenantResponse> response = tenants.stream()
                .map(tenantMapper::toResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /**
     * Gets tenants by organization type.
     */
    @GetMapping("/type/{type}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('TENANT_VIEWER')")
    @Operation(summary = "Get tenants by organization type",
            description = "Lists all tenants of a specific organization type")
    public ResponseEntity<List<TenantResponse>> getTenantsByType(
            @PathVariable OrganizationType type) {

        log.debug("Fetching tenants by type: {}", type);

        List<Tenant> tenants = tenantService.getTenantsByOrganizationType(type);
        List<TenantResponse> response = tenants.stream()
                .map(tenantMapper::toResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /**
     * Gets expiring tenants.
     */
    @GetMapping("/expiring")
    @PreAuthorize("hasRole('ADMIN') or hasRole('BILLING_MANAGER')")
    @Operation(summary = "Get expiring tenants",
            description = "Lists tenants with subscriptions expiring soon")
    public ResponseEntity<List<TenantResponse>> getExpiringTenants(
            @RequestParam(defaultValue = "30") int daysAhead) {

        log.debug("Fetching tenants expiring in {} days", daysAhead);

        List<Tenant> tenants = tenantService.getExpiringTenants(daysAhead);
        List<TenantResponse> response = tenants.stream()
                .map(tenantMapper::toResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /**
     * Gets trial tenants ending soon.
     */
    @GetMapping("/trials-ending")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SALES_MANAGER')")
    @Operation(summary = "Get trials ending soon",
            description = "Lists tenants with trials ending in specified days")
    public ResponseEntity<List<TenantResponse>> getTrialsEndingSoon(
            @RequestParam(defaultValue = "7") int daysAhead) {

        log.debug("Fetching trials ending in {} days", daysAhead);

        List<Tenant> tenants = tenantService.getTrialsEndingSoon(daysAhead);
        List<TenantResponse> response = tenants.stream()
                .map(tenantMapper::toResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /**
     * Gets tenant statistics.
     */
    @GetMapping("/statistics")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get tenant statistics",
            description = "Retrieves statistical information about all tenants")
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

    // Hierarchy endpoints

    /**
     * Creates a child tenant.
     */
    @PostMapping("/{parentId}/children")
    @PreAuthorize("hasRole('ADMIN') or @tenantSecurity.isTenantAdmin(#parentId)")
    @Operation(summary = "Create child tenant",
            description = "Creates a child tenant under a parent organization")
    public ResponseEntity<TenantResponse> createChildTenant(
            @PathVariable UUID parentId,
            @Valid @RequestBody CreateTenantRequest request) {

        log.info("Creating child tenant for parent: {}", parentId);

        Tenant childTenant = tenantMapper.toEntity(request);
        childTenant = hierarchyService.createChildTenant(parentId, childTenant);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(tenantMapper.toResponse(childTenant));
    }

    /**
     * Gets tenant hierarchy.
     */
    @GetMapping("/{id}/hierarchy")
    @PreAuthorize("hasRole('ADMIN') or @tenantSecurity.canAccessTenant(#id)")
    @Operation(summary = "Get tenant hierarchy",
            description = "Retrieves the complete hierarchy for a tenant")
    public ResponseEntity<OrganizationHierarchyService.TenantHierarchy> getTenantHierarchy(
            @PathVariable UUID id) {

        log.debug("Fetching hierarchy for tenant: {}", id);

        OrganizationHierarchyService.TenantHierarchy hierarchy =
                hierarchyService.getTenantHierarchy(id);

        return ResponseEntity.ok(hierarchy);
    }

    /**
     * Gets child tenants.
     */
    @GetMapping("/{id}/children")
    @PreAuthorize("hasRole('ADMIN') or @tenantSecurity.canAccessTenant(#id)")
    @Operation(summary = "Get child tenants",
            description = "Lists all child tenants of a parent")
    public ResponseEntity<List<TenantResponse>> getChildTenants(@PathVariable UUID id) {
        log.debug("Fetching children for tenant: {}", id);

        List<Tenant> children = hierarchyService.getChildren(id);
        List<TenantResponse> response = children.stream()
                .map(tenantMapper::toResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /**
     * Self-service endpoint for current tenant.
     */
    @GetMapping("/me")
    @Operation(summary = "Get current tenant",
            description = "Retrieves information about the authenticated tenant")
    public ResponseEntity<TenantResponse> getCurrentTenant(
            @RequestHeader("X-Tenant-ID") String tenantId) {

        log.debug("Fetching current tenant: {}", tenantId);

        return tenantService.getTenantById(UUID.fromString(tenantId))
                .map(tenantMapper::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}