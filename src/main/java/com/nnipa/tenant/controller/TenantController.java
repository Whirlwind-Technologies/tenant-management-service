package com.nnipa.tenant.controller;

import com.nnipa.tenant.dto.request.CreateTenantRequest;
import com.nnipa.tenant.dto.request.UpdateTenantRequest;
import com.nnipa.tenant.dto.response.ApiResponse;
import com.nnipa.tenant.dto.response.TenantResponse;
import com.nnipa.tenant.dto.response.TenantStatisticsResponse;
import com.nnipa.tenant.entity.Tenant;
import com.nnipa.tenant.enums.OrganizationType;
import com.nnipa.tenant.mapper.TenantMapper;
import com.nnipa.tenant.service.OrganizationHierarchyService;
import com.nnipa.tenant.service.TenantService;
import com.nnipa.tenant.util.ResponseUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
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
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "Tenant created successfully",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid request"
            )
    })
    public ResponseEntity<ApiResponse<TenantResponse>> createTenant(
            @Valid @RequestBody CreateTenantRequest request) {

        log.info("Creating tenant: {} ({})", request.getName(), request.getOrganizationType());

        Tenant tenant = tenantMapper.toEntity(request);
        tenant = tenantService.createTenant(tenant);
        TenantResponse response = tenantMapper.toResponse(tenant);

        return ResponseUtil.created(response,
                String.format("Tenant '%s' created successfully", tenant.getName()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get tenant by ID")
    public ResponseEntity<ApiResponse<TenantResponse>> getTenant(@PathVariable UUID id) {
        log.debug("Fetching tenant: {}", id);

        return ResponseUtil.fromOptional(
                tenantService.getTenantById(id),
                tenantMapper::toResponse,
                String.format("Tenant with ID '%s' not found", id)
        );
    }

    @GetMapping("/code/{code}")
    @Operation(summary = "Get tenant by code")
    public ResponseEntity<ApiResponse<TenantResponse>> getTenantByCode(@PathVariable String code) {
        log.debug("Fetching tenant by code: {}", code);

        return ResponseUtil.fromOptional(
                tenantService.getTenantByCode(code),
                tenantMapper::toResponse,
                String.format("Tenant with code '%s' not found", code)
        );
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update tenant")
    public ResponseEntity<ApiResponse<TenantResponse>> updateTenant(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTenantRequest request) {

        log.info("Updating tenant: {}", id);

        Tenant updates = tenantMapper.toEntity(request);
        Tenant tenant = tenantService.updateTenant(id, updates);

        return ResponseUtil.success(
                tenantMapper.toResponse(tenant),
                "Tenant updated successfully"
        );
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete tenant")
    public ResponseEntity<ApiResponse<Void>> deleteTenant(@PathVariable UUID id) {
        log.warn("Marking tenant for deletion: {}", id);

        tenantService.markForDeletion(id);

        return ResponseUtil.noContent("Tenant marked for deletion successfully");
    }

    @PostMapping("/{id}/activate")
    @Operation(summary = "Activate tenant")
    public ResponseEntity<ApiResponse<TenantResponse>> activateTenant(@PathVariable UUID id) {
        log.info("Activating tenant: {}", id);

        Tenant tenant = tenantService.activateTenant(id);

        return ResponseUtil.success(
                tenantMapper.toResponse(tenant),
                "Tenant activated successfully"
        );
    }

    @PostMapping("/{id}/suspend")
    @Operation(summary = "Suspend tenant")
    public ResponseEntity<ApiResponse<TenantResponse>> suspendTenant(
            @PathVariable UUID id,
            @RequestParam String reason) {

        log.warn("Suspending tenant: {} (Reason: {})", id, reason);

        Tenant tenant = tenantService.suspendTenant(id, reason);

        return ResponseUtil.success(
                tenantMapper.toResponse(tenant),
                String.format("Tenant suspended: %s", reason)
        );
    }

    @PostMapping("/{id}/verify")
    @Operation(summary = "Verify tenant")
    public ResponseEntity<ApiResponse<TenantResponse>> verifyTenant(
            @PathVariable UUID id,
            @RequestParam String verifiedBy,
            @RequestParam(required = false) String verificationDocument) {

        log.info("Verifying tenant: {} by {}", id, verifiedBy);

        Tenant tenant = tenantService.verifyTenant(id, verifiedBy, verificationDocument);

        return ResponseUtil.success(
                tenantMapper.toResponse(tenant),
                String.format("Tenant verified by %s", verifiedBy)
        );
    }

    @GetMapping
    @Operation(summary = "List tenants with pagination")
    public ResponseEntity<ApiResponse<List<TenantResponse>>> listTenants(
            @RequestParam(required = false) OrganizationType organizationType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        log.debug("Listing tenants - Type: {}, Status: {}, Search: {}",
                organizationType, status, search);

        Page<Tenant> tenants = tenantService.searchTenants(search, pageable);
        Page<TenantResponse> responsePage = tenants.map(tenantMapper::toResponse);

        return ResponseUtil.paginated(responsePage);
    }

    @GetMapping("/me")
    @Operation(summary = "Get current tenant")
    public ResponseEntity<ApiResponse<TenantResponse>> getCurrentTenant(
            @RequestHeader("X-Tenant-ID") String tenantId) {

        log.debug("Fetching current tenant: {}", tenantId);

        return ResponseUtil.fromOptional(
                tenantService.getTenantById(UUID.fromString(tenantId)),
                tenantMapper::toResponse,
                "Current tenant not found"
        );
    }

    @GetMapping("/statistics")
    @Operation(summary = "Get tenant statistics")
    public ResponseEntity<ApiResponse<TenantStatisticsResponse>> getTenantStatistics() {
        log.debug("Fetching tenant statistics");

        TenantService.TenantStatistics stats = tenantService.getTenantStatistics();
        TenantStatisticsResponse response = TenantStatisticsResponse.builder()
                .totalTenants(stats.getTotalTenants())
                .activeTenants(stats.getActiveTenants())
                .statisticsByType(stats.getStatisticsByType())
                .build();

        return ResponseUtil.success(response, "Statistics retrieved successfully");
    }

    @GetMapping("/{id}/children")
    @Operation(summary = "Get child tenants")
    public ResponseEntity<ApiResponse<List<TenantResponse>>> getChildTenants(@PathVariable UUID id) {
        log.debug("Fetching children for tenant: {}", id);

        List<Tenant> children = hierarchyService.getChildren(id);
        List<TenantResponse> response = children.stream()
                .map(tenantMapper::toResponse)
                .collect(Collectors.toList());

        return ResponseUtil.success(response,
                String.format("Found %d child tenants", response.size()));
    }
}