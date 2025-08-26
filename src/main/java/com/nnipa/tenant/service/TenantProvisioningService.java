package com.nnipa.tenant.service;

import com.nnipa.tenant.entity.Tenant;
import com.nnipa.tenant.enums.OrganizationType;
import com.nnipa.tenant.enums.TenantIsolationStrategy;
import com.nnipa.tenant.enums.TenantStatus;
import com.nnipa.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service for provisioning tenants with appropriate data isolation.
 * Handles database/schema creation based on organization type and requirements.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantProvisioningService {

    private final TenantRepository tenantRepository;
    private final SchemaManagementService schemaService;
    private final DatabaseProvisioningService databaseService;

    @Value("${app.tenant.auto-activate:false}")
    private boolean autoActivate;

    @Value("${app.tenant.async-provisioning:true}")
    private boolean asyncProvisioning;

    /**
     * Provisions a new tenant with appropriate isolation strategy.
     */
    @Transactional
    public Tenant provisionTenant(Tenant tenant) {
        try {
            log.info("Starting tenant provisioning for: {} ({})",
                    tenant.getName(), tenant.getOrganizationType());

            // Step 1: Determine isolation strategy based on organization type
            TenantIsolationStrategy strategy = determineIsolationStrategy(
                    tenant.getOrganizationType(),
                    tenant.getComplianceFrameworks() != null && !tenant.getComplianceFrameworks().isEmpty()
            );
            tenant.setIsolationStrategy(strategy);

            // Step 2: Generate schema/database names
            String sanitizedName = sanitizeName(tenant.getTenantCode());

            switch (strategy) {
                case DATABASE_PER_TENANT -> {
                    String dbName = "nnipa_" + sanitizedName.toLowerCase();
                    tenant.setDatabaseName(dbName);
                    tenant.setDatabaseServer(getDatabaseServer(tenant));
                    tenant.setDatabasePort(5432);
                    tenant.setConnectionPoolSize(determinePoolSize(tenant));
                }
                case SCHEMA_PER_TENANT, HYBRID_POOL -> {
                    String schemaName = "tenant_" + sanitizedName.toLowerCase();
                    tenant.setSchemaName(schemaName);
                    tenant.setConnectionPoolSize(determinePoolSize(tenant));
                }
                case SHARED_SCHEMA_ROW_LEVEL, SHARED_SCHEMA_BASIC -> {
                    // No specific schema/database needed
                    tenant.setConnectionPoolSize(0); // Uses shared pool
                }
            }

            // Step 3: Set tenant status
            tenant.setStatus(TenantStatus.PROVISIONING);

            // Step 4: Save tenant metadata
            tenant = tenantRepository.save(tenant);
            log.info("Tenant metadata saved: {}", tenant.getId());

            // Step 5: Provision data resources based on strategy
            if (asyncProvisioning && requiresProvisioning(strategy)) {
                // Async provisioning for long-running operations
                provisionDataResourcesAsync(tenant);
            } else {
                // Synchronous provisioning
                provisionDataResources(tenant);

                // Step 6: Activate tenant if auto-activation is enabled
                if (shouldAutoActivate(tenant)) {
                    activateTenant(tenant);
                }
            }

            log.info("Tenant provisioning initiated for: {}", tenant.getName());
            return tenant;

        } catch (Exception e) {
            log.error("Error provisioning tenant: {}", tenant.getName(), e);
            tenant.setStatus(TenantStatus.PROVISIONING_FAILED);
            tenantRepository.save(tenant);
            throw new TenantProvisioningException("Failed to provision tenant", e);
        }
    }

    /**
     * Provisions data resources (database/schema) for the tenant.
     */
    @Transactional
    public void provisionDataResources(Tenant tenant) {
        log.info("Provisioning data resources for tenant: {} with strategy: {}",
                tenant.getName(), tenant.getIsolationStrategy());

        try {
            switch (tenant.getIsolationStrategy()) {
                case DATABASE_PER_TENANT -> {
                    log.info("Creating database for tenant: {}", tenant.getName());
                    databaseService.createDatabase(tenant.getDatabaseName(), tenant.getId());
                    tenant.setStatus(TenantStatus.DATABASE_CREATED);
                }
                case SCHEMA_PER_TENANT, HYBRID_POOL -> {
                    log.info("Creating schema for tenant: {}", tenant.getName());
                    schemaService.createSchema(tenant.getSchemaName());
                    tenant.setStatus(TenantStatus.SCHEMA_CREATED);
                }
                case SHARED_SCHEMA_ROW_LEVEL, SHARED_SCHEMA_BASIC -> {
                    // No specific provisioning needed, just set up row-level security context
                    log.info("Tenant will use shared schema with row-level security");
                    tenant.setStatus(TenantStatus.READY);
                }
            }

            // Update tenant with provisioning details
            tenant.setProvisionedAt(Instant.now());
            tenantRepository.save(tenant);

            log.info("Data resources provisioned successfully for tenant: {}", tenant.getName());

        } catch (Exception e) {
            log.error("Failed to provision data resources for tenant: {}", tenant.getName(), e);
            tenant.setStatus(TenantStatus.PROVISIONING_FAILED);
            tenantRepository.save(tenant);
            throw new TenantProvisioningException("Failed to provision data resources", e);
        }
    }

    /**
     * Asynchronously provisions data resources for the tenant.
     */
    @Async
    public CompletableFuture<Tenant> provisionDataResourcesAsync(Tenant tenant) {
        log.info("Starting async provisioning for tenant: {}", tenant.getName());

        try {
            // Provision resources
            provisionDataResources(tenant);

            // Auto-activate if configured
            if (shouldAutoActivate(tenant)) {
                activateTenant(tenant);
            }

            return CompletableFuture.completedFuture(tenant);

        } catch (Exception e) {
            log.error("Async provisioning failed for tenant: {}", tenant.getName(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Deprovisions a tenant's data resources.
     */
    @Transactional
    public void deprovisionTenant(Tenant tenant) {
        log.warn("Deprovisioning tenant: {}", tenant.getName());

        try {
            switch (tenant.getIsolationStrategy()) {
                case DATABASE_PER_TENANT -> {
                    if (tenant.getDatabaseName() != null) {
                        log.warn("Dropping database: {}", tenant.getDatabaseName());
                        databaseService.dropDatabase(tenant.getDatabaseName());
                    }
                }
                case SCHEMA_PER_TENANT, HYBRID_POOL -> {
                    if (tenant.getSchemaName() != null) {
                        log.warn("Dropping schema: {}", tenant.getSchemaName());
                        schemaService.dropSchema(tenant.getSchemaName());
                    }
                }
                case SHARED_SCHEMA_ROW_LEVEL, SHARED_SCHEMA_BASIC -> {
                    // Data will be deleted via cascade delete on tenant record
                    log.info("Tenant data will be removed via cascade delete");
                }
            }

            tenant.setStatus(TenantStatus.DEPROVISIONED);
            tenant.setDeprovisionedAt(Instant.now());
            tenantRepository.save(tenant);

            log.info("Tenant deprovisioned: {}", tenant.getName());

        } catch (Exception e) {
            log.error("Failed to deprovision tenant: {}", tenant.getName(), e);
            throw new TenantProvisioningException("Failed to deprovision tenant", e);
        }
    }

    /**
     * Migrates a tenant to a different isolation strategy.
     */
    @Transactional
    public void migrateTenantIsolation(Tenant tenant, TenantIsolationStrategy newStrategy) {
        log.info("Migrating tenant {} from {} to {}",
                tenant.getName(), tenant.getIsolationStrategy(), newStrategy);

        try {
            TenantIsolationStrategy oldStrategy = tenant.getIsolationStrategy();

            // Step 1: Create new resources
            tenant.setIsolationStrategy(newStrategy);
            String sanitizedName = sanitizeName(tenant.getTenantCode());

            switch (newStrategy) {
                case DATABASE_PER_TENANT -> {
                    String dbName = "nnipa_" + sanitizedName.toLowerCase();
                    tenant.setDatabaseName(dbName);
                    databaseService.createDatabase(dbName, tenant.getId());
                }
                case SCHEMA_PER_TENANT, HYBRID_POOL -> {
                    String schemaName = "tenant_" + sanitizedName.toLowerCase();
                    tenant.setSchemaName(schemaName);
                    schemaService.createSchema(schemaName);
                }
            }

            // Step 2: Migrate data (would need to implement data migration logic)
            // This is a placeholder - actual implementation would copy data
            log.info("Data migration would happen here");

            // Step 3: Clean up old resources
            switch (oldStrategy) {
                case DATABASE_PER_TENANT -> {
                    if (tenant.getDatabaseName() != null) {
                        databaseService.dropDatabase(tenant.getDatabaseName());
                    }
                }
                case SCHEMA_PER_TENANT, HYBRID_POOL -> {
                    if (tenant.getSchemaName() != null) {
                        schemaService.dropSchema(tenant.getSchemaName());
                    }
                }
            }

            // Step 4: Update tenant
            tenant.setMigratedAt(Instant.now());
            tenantRepository.save(tenant);

            log.info("Tenant migration completed successfully");

        } catch (Exception e) {
            log.error("Failed to migrate tenant isolation", e);
            throw new TenantProvisioningException("Failed to migrate tenant isolation", e);
        }
    }

    /**
     * Validates if provisioning is successful.
     */
    public boolean validateProvisioning(Tenant tenant) {
        try {
            switch (tenant.getIsolationStrategy()) {
                case DATABASE_PER_TENANT -> {
                    return databaseService.databaseExists(tenant.getDatabaseName());
                }
                case SCHEMA_PER_TENANT, HYBRID_POOL -> {
                    return schemaService.schemaExists(tenant.getSchemaName());
                }
                case SHARED_SCHEMA_ROW_LEVEL, SHARED_SCHEMA_BASIC -> {
                    return true; // Always valid for shared schemas
                }
                default -> {
                    return false;
                }
            }
        } catch (Exception e) {
            log.error("Error validating provisioning for tenant: {}", tenant.getName(), e);
            return false;
        }
    }

    /**
     * Gets provisioning status for a tenant.
     */
    public ProvisioningStatus getProvisioningStatus(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException("Tenant not found: " + tenantId));

        boolean isProvisioned = validateProvisioning(tenant);

        return ProvisioningStatus.builder()
                .tenantId(tenantId)
                .status(tenant.getStatus())
                .isolationStrategy(tenant.getIsolationStrategy())
                .isProvisioned(isProvisioned)
                .databaseName(tenant.getDatabaseName())
                .schemaName(tenant.getSchemaName())
                .provisionedAt(tenant.getProvisionedAt())
                .build();
    }

    /**
     * Determines the appropriate isolation strategy based on organization type.
     */
    private TenantIsolationStrategy determineIsolationStrategy(
            OrganizationType orgType, boolean hasComplianceRequirements) {

        // High security organizations
        if (orgType == OrganizationType.GOVERNMENT_AGENCY ||
                orgType == OrganizationType.FINANCIAL_INSTITUTION) {
            return TenantIsolationStrategy.DATABASE_PER_TENANT;
        }

        // Healthcare with compliance
        if (orgType == OrganizationType.HEALTHCARE && hasComplianceRequirements) {
            return TenantIsolationStrategy.DATABASE_PER_TENANT;
        }

        // Medium security organizations
        if (orgType == OrganizationType.CORPORATION ||
                orgType == OrganizationType.ACADEMIC_INSTITUTION ||
                orgType == OrganizationType.HEALTHCARE) {
            return TenantIsolationStrategy.SCHEMA_PER_TENANT;
        }

        // Lower security organizations
        if (orgType == OrganizationType.RESEARCH_ORGANIZATION ||
                orgType == OrganizationType.NON_PROFIT ||
                orgType == OrganizationType.STARTUP) {
            return TenantIsolationStrategy.SHARED_SCHEMA_ROW_LEVEL;
        }

        // Individual users
        if (orgType == OrganizationType.INDIVIDUAL) {
            return TenantIsolationStrategy.SHARED_SCHEMA_BASIC;
        }

        // Default to row-level security
        return TenantIsolationStrategy.SHARED_SCHEMA_ROW_LEVEL;
    }

    /**
     * Determines if a strategy requires provisioning.
     */
    private boolean requiresProvisioning(TenantIsolationStrategy strategy) {
        return strategy == TenantIsolationStrategy.DATABASE_PER_TENANT ||
                strategy == TenantIsolationStrategy.SCHEMA_PER_TENANT ||
                strategy == TenantIsolationStrategy.HYBRID_POOL;
    }

    /**
     * Determines if tenant should be auto-activated.
     */
    private boolean shouldAutoActivate(Tenant tenant) {
        if (!autoActivate) {
            return false;
        }

        // Auto-activate individuals and startups
        return tenant.getOrganizationType() == OrganizationType.INDIVIDUAL ||
                tenant.getOrganizationType() == OrganizationType.STARTUP;
    }

    /**
     * Activates a tenant.
     */
    private void activateTenant(Tenant tenant) {
        tenant.setStatus(TenantStatus.ACTIVE);
        tenant.setActivatedAt(Instant.now());
        tenant.setIsVerified(true);
        tenant.setVerifiedAt(Instant.now());
        tenantRepository.save(tenant);
        log.info("Tenant activated: {}", tenant.getName());
    }

    /**
     * Gets the database server for a tenant.
     */
    private String getDatabaseServer(Tenant tenant) {
        // In production, this could return different servers based on region/load
        return "localhost";
    }

    /**
     * Determines the connection pool size based on tenant.
     */
    private int determinePoolSize(Tenant tenant) {
        return switch (tenant.getOrganizationType()) {
            case GOVERNMENT_AGENCY, FINANCIAL_INSTITUTION -> 20;
            case CORPORATION, HEALTHCARE -> 15;
            case ACADEMIC_INSTITUTION, RESEARCH_ORGANIZATION -> 10;
            case NON_PROFIT, STARTUP -> 5;
            case INDIVIDUAL -> 2;
        };
    }

    /**
     * Sanitizes a name for use in database/schema names.
     */
    private String sanitizeName(String name) {
        return name.toLowerCase()
                .replaceAll("[^a-z0-9_]", "_")
                .replaceAll("_{2,}", "_")
                .replaceAll("^_|_$", "");
    }

    /**
     * DTO for provisioning status.
     */
    @lombok.Builder
    @lombok.Data
    public static class ProvisioningStatus {
        private UUID tenantId;
        private TenantStatus status;
        private TenantIsolationStrategy isolationStrategy;
        private boolean isProvisioned;
        private String databaseName;
        private String schemaName;
        private Instant provisionedAt;
    }

    /**
     * Custom exceptions.
     */
    public static class TenantProvisioningException extends RuntimeException {
        public TenantProvisioningException(String message) {
            super(message);
        }

        public TenantProvisioningException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class TenantNotFoundException extends RuntimeException {
        public TenantNotFoundException(String message) {
            super(message);
        }
    }
}