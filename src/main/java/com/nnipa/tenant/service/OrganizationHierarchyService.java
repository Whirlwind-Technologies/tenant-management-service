package com.nnipa.tenant.service;

import com.nnipa.tenant.entity.Tenant;
import com.nnipa.tenant.enums.OrganizationType;
import com.nnipa.tenant.enums.TenantStatus;
import com.nnipa.tenant.repository.TenantRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing organizational hierarchies and relationships between tenants.
 * Supports parent-child relationships for departments, subsidiaries, and divisions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrganizationHierarchyService {

    private final TenantRepository tenantRepository;
    private final TenantProvisioningService provisioningService;

    /**
     * Creates a child tenant under a parent organization.
     */
    @Transactional
    public Tenant createChildTenant(UUID parentId, Tenant childTenant) {
        log.info("Creating child tenant for parent: {}", parentId);

        Tenant parent = tenantRepository.findById(parentId)
                .orElseThrow(() -> new TenantNotFoundException("Parent tenant not found"));

        // Validate parent can have children
        validateParentEligibility(parent);

        // Set parent relationship
        childTenant.setParentTenant(parent);

        // Inherit certain properties from parent
        inheritParentProperties(childTenant, parent);

        // Validate hierarchy depth
        validateHierarchyDepth(childTenant);

        // Save child tenant
        childTenant = tenantRepository.save(childTenant);

        // Provision resources (may share parent's database/schema)
        childTenant = provisioningService.provisionTenant(childTenant);

        log.info("Child tenant created: {} under parent: {}",
                childTenant.getName(), parent.getName());

        return childTenant;
    }

    /**
     * Gets the complete hierarchy for a tenant (up and down).
     */
    public TenantHierarchy getTenantHierarchy(UUID tenantId) {
        log.debug("Getting hierarchy for tenant: {}", tenantId);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException("Tenant not found"));

        // Get ancestors
        List<Tenant> ancestors = getAncestors(tenant);

        // Get descendants
        List<Tenant> descendants = getDescendants(tenant);

        // Build hierarchy tree
        HierarchyNode root = buildHierarchyTree(tenant);

        return TenantHierarchy.builder()
                .currentTenant(tenant)
                .ancestors(ancestors)
                .descendants(descendants)
                .hierarchyTree(root)
                .depth(calculateDepth(tenant))
                .breadth(descendants.size())
                .build();
    }

    /**
     * Moves a tenant to a new parent.
     */
    @Transactional
    public Tenant changeParent(UUID tenantId, UUID newParentId) {
        log.info("Changing parent for tenant: {} to: {}", tenantId, newParentId);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException("Tenant not found"));

        Tenant newParent = null;
        if (newParentId != null) {
            newParent = tenantRepository.findById(newParentId)
                    .orElseThrow(() -> new TenantNotFoundException("New parent tenant not found"));

            // Validate no circular reference
            validateNoCircularReference(tenant, newParent);
        }

        // Update parent
        Tenant oldParent = tenant.getParentTenant();
        tenant.setParentTenant(newParent);

        // Update inherited properties if needed
        if (newParent != null) {
            updateInheritedProperties(tenant, newParent);
        }

        tenant = tenantRepository.save(tenant);

        log.info("Parent changed for tenant: {} from: {} to: {}",
                tenant.getName(),
                oldParent != null ? oldParent.getName() : "none",
                newParent != null ? newParent.getName() : "none");

        return tenant;
    }

    /**
     * Gets all root tenants (no parent).
     */
    public List<Tenant> getRootTenants() {
        log.debug("Getting root tenants");
        return tenantRepository.findByParentTenant(null);
    }

    /**
     * Gets direct children of a tenant.
     */
    public List<Tenant> getChildren(UUID parentId) {
        log.debug("Getting children for tenant: {}", parentId);

        Tenant parent = tenantRepository.findById(parentId)
                .orElseThrow(() -> new TenantNotFoundException("Parent tenant not found"));

        return tenantRepository.findByParentTenant(parent);
    }

    /**
     * Propagates settings changes to child tenants.
     */
    @Transactional
    public void propagateSettings(UUID parentId, Map<String, Object> settings, boolean recursive) {
        log.info("Propagating settings from parent: {}, recursive: {}", parentId, recursive);

        Tenant parent = tenantRepository.findById(parentId)
                .orElseThrow(() -> new TenantNotFoundException("Parent tenant not found"));

        List<Tenant> children = getChildren(parentId);

        for (Tenant child : children) {
            applyInheritedSettings(child, settings);
            tenantRepository.save(child);

            if (recursive) {
                propagateSettings(child.getId(), settings, true);
            }
        }

        log.info("Settings propagated to {} children", children.size());
    }

    /**
     * Calculates aggregate usage for a tenant and its children.
     */
    public AggregateUsage calculateAggregateUsage(UUID tenantId) {
        log.debug("Calculating aggregate usage for tenant: {}", tenantId);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException("Tenant not found"));

        List<Tenant> allTenants = new ArrayList<>();
        allTenants.add(tenant);
        allTenants.addAll(getDescendants(tenant));

        // Aggregate metrics
        int totalUsers = 0;
        int totalProjects = 0;
        int totalStorage = 0;

        for (Tenant t : allTenants) {
            totalUsers += t.getMaxUsers() != null ? t.getMaxUsers() : 0;
            totalProjects += t.getMaxProjects() != null ? t.getMaxProjects() : 0;
            totalStorage += t.getStorageQuotaGb() != null ? t.getStorageQuotaGb() : 0;
        }

        return AggregateUsage.builder()
                .tenantId(tenantId)
                .tenantCount(allTenants.size())
                .totalUsers(totalUsers)
                .totalProjects(totalProjects)
                .totalStorageGb(totalStorage)
                .tenants(allTenants)
                .build();
    }

    /**
     * Validates hierarchy constraints.
     */
    public ValidationResult validateHierarchy(UUID tenantId) {
        log.debug("Validating hierarchy for tenant: {}", tenantId);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException("Tenant not found"));

        List<String> violations = new ArrayList<>();

        // Check depth limit
        int depth = calculateDepth(tenant);
        if (depth > getMaxDepthForOrgType(tenant.getOrganizationType())) {
            violations.add("Hierarchy depth exceeds limit: " + depth);
        }

        // Check breadth limit
        List<Tenant> children = getChildren(tenantId);
        if (children.size() > getMaxChildrenForOrgType(tenant.getOrganizationType())) {
            violations.add("Number of children exceeds limit: " + children.size());
        }

        // Check for circular references
        if (hasCircularReference(tenant)) {
            violations.add("Circular reference detected in hierarchy");
        }

        // Check status consistency
        if (tenant.getStatus() == TenantStatus.ACTIVE &&
                tenant.getParentTenant() != null &&
                tenant.getParentTenant().getStatus() != TenantStatus.ACTIVE) {
            violations.add("Active tenant cannot have inactive parent");
        }

        return ValidationResult.builder()
                .valid(violations.isEmpty())
                .violations(violations)
                .build();
    }

    // Helper methods

    private void validateParentEligibility(Tenant parent) {
        // Check if parent org type can have children
        OrganizationType type = parent.getOrganizationType();
        if (type == OrganizationType.INDIVIDUAL) {
            throw new IllegalStateException("Individual tenants cannot have children");
        }

        // Check parent status
        if (parent.getStatus() != TenantStatus.ACTIVE) {
            throw new IllegalStateException("Parent tenant must be active");
        }

        // Check hierarchy depth
        int currentDepth = calculateDepth(parent);
        if (currentDepth >= getMaxDepthForOrgType(type)) {
            throw new IllegalStateException("Maximum hierarchy depth reached");
        }
    }

    private void inheritParentProperties(Tenant child, Tenant parent) {
        // Inherit organization type if not set
        if (child.getOrganizationType() == null) {
            child.setOrganizationType(parent.getOrganizationType());
        }

        // Inherit compliance frameworks
        if (parent.getComplianceFrameworks() != null) {
            child.setComplianceFrameworks(new HashSet<>(parent.getComplianceFrameworks()));
        }

        // Inherit data residency
        if (child.getDataResidencyRegion() == null) {
            child.setDataResidencyRegion(parent.getDataResidencyRegion());
        }

        // Inherit security level
        if (child.getSecurityLevel() == null) {
            child.setSecurityLevel(parent.getSecurityLevel());
        }

        // Share isolation strategy for cost efficiency
        if (parent.getIsolationStrategy() != null) {
            child.setIsolationStrategy(parent.getIsolationStrategy());
            if (parent.getDatabaseName() != null) {
                child.setDatabaseName(parent.getDatabaseName());
            }
        }
    }

    private void updateInheritedProperties(Tenant tenant, Tenant newParent) {
        // Update properties that should be inherited
        tenant.setDataResidencyRegion(newParent.getDataResidencyRegion());
        tenant.setSecurityLevel(newParent.getSecurityLevel());

        // Merge compliance frameworks
        if (newParent.getComplianceFrameworks() != null) {
            tenant.getComplianceFrameworks().addAll(newParent.getComplianceFrameworks());
        }
    }

    private List<Tenant> getAncestors(Tenant tenant) {
        List<Tenant> ancestors = new ArrayList<>();
        Tenant current = tenant.getParentTenant();

        while (current != null) {
            ancestors.add(current);
            current = current.getParentTenant();
        }

        return ancestors;
    }

    private List<Tenant> getDescendants(Tenant tenant) {
        List<Tenant> descendants = new ArrayList<>();
        Queue<Tenant> queue = new LinkedList<>();
        queue.addAll(tenantRepository.findByParentTenant(tenant));

        while (!queue.isEmpty()) {
            Tenant current = queue.poll();
            descendants.add(current);
            queue.addAll(tenantRepository.findByParentTenant(current));
        }

        return descendants;
    }

    private HierarchyNode buildHierarchyTree(Tenant tenant) {
        HierarchyNode node = HierarchyNode.builder()
                .tenant(tenant)
                .children(new ArrayList<>())
                .build();

        List<Tenant> children = tenantRepository.findByParentTenant(tenant);
        for (Tenant child : children) {
            node.getChildren().add(buildHierarchyTree(child));
        }

        return node;
    }

    private int calculateDepth(Tenant tenant) {
        int depth = 0;
        Tenant current = tenant.getParentTenant();

        while (current != null) {
            depth++;
            current = current.getParentTenant();
        }

        return depth;
    }

    private void validateHierarchyDepth(Tenant tenant) {
        int depth = calculateDepth(tenant);
        int maxDepth = getMaxDepthForOrgType(tenant.getOrganizationType());

        if (depth > maxDepth) {
            throw new IllegalStateException(
                    String.format("Hierarchy depth %d exceeds maximum %d for organization type %s",
                            depth, maxDepth, tenant.getOrganizationType())
            );
        }
    }

    private void validateNoCircularReference(Tenant tenant, Tenant newParent) {
        // Check if newParent is a descendant of tenant
        List<Tenant> descendants = getDescendants(tenant);
        if (descendants.contains(newParent)) {
            throw new IllegalStateException("Cannot create circular reference in hierarchy");
        }
    }

    private boolean hasCircularReference(Tenant tenant) {
        Set<UUID> visited = new HashSet<>();
        Tenant current = tenant;

        while (current != null) {
            if (visited.contains(current.getId())) {
                return true;
            }
            visited.add(current.getId());
            current = current.getParentTenant();
        }

        return false;
    }

    private int getMaxDepthForOrgType(OrganizationType type) {
        return switch (type) {
            case GOVERNMENT_AGENCY, CORPORATION -> 5;
            case ACADEMIC_INSTITUTION -> 4;
            case HEALTHCARE, FINANCIAL_INSTITUTION -> 3;
            default -> 2;
        };
    }

    private int getMaxChildrenForOrgType(OrganizationType type) {
        return switch (type) {
            case GOVERNMENT_AGENCY -> 100;
            case CORPORATION -> 50;
            case ACADEMIC_INSTITUTION -> 30;
            default -> 10;
        };
    }

    private void applyInheritedSettings(Tenant tenant, Map<String, Object> settings) {
        // Apply settings that should be inherited
        // This would update specific settings based on inheritance rules
        log.debug("Applying inherited settings to tenant: {}", tenant.getName());
    }

    // DTOs

    @Data
    @Builder
    public static class TenantHierarchy {
        private Tenant currentTenant;
        private List<Tenant> ancestors;
        private List<Tenant> descendants;
        private HierarchyNode hierarchyTree;
        private int depth;
        private int breadth;
    }

    @Data
    @Builder
    public static class HierarchyNode {
        private Tenant tenant;
        private List<HierarchyNode> children;
    }

    @Data
    @Builder
    public static class AggregateUsage {
        private UUID tenantId;
        private int tenantCount;
        private int totalUsers;
        private int totalProjects;
        private int totalStorageGb;
        private List<Tenant> tenants;
    }

    @Data
    @Builder
    public static class ValidationResult {
        private boolean valid;
        private List<String> violations;
    }

    // Exceptions

    public static class TenantNotFoundException extends RuntimeException {
        public TenantNotFoundException(String message) {
            super(message);
        }
    }
}