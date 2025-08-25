package com.nnipa.tenant.service;

import com.nnipa.tenant.config.MultiTenantDataSourceConfig;
import com.nnipa.tenant.config.SchemaService;
import com.nnipa.tenant.entity.Tenant;
import com.nnipa.tenant.enums.OrganizationType;
import com.nnipa.tenant.enums.TenantIsolationStrategy;
import com.nnipa.tenant.enums.TenantStatus;
import com.nnipa.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.UUID;

/**
 * Service for provisioning new tenants with appropriate isolation strategy.
 * Handles database/schema creation based on organization type and requirements.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantProvisioningService {

    private final TenantRepository tenantRepository;
    private final SchemaService schemaService;
    private final MultiTenantDataSourceConfig dataSourceConfig;

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
                    tenant.getComplianceFrameworks().size() > 0
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
            tenant.setStatus(TenantStatus.PENDING_VERIFICATION);

            // Step 4: Save tenant metadata
            tenant = tenantRepository.save(tenant);
            log.info("Tenant metadata saved: {}", tenant.getId());

            // Step 5: Provision database/schema based on strategy
            provisionDataResources(tenant);

            // Step 6: Activate tenant if auto-activation is enabled
            if (shouldAutoActivate(tenant)) {
                tenant.setStatus(TenantStatus.ACTIVE);
                tenant.setActivatedAt(Instant.now());
                tenant.setIsVerified(true);
                tenant.setVerifiedAt(Instant.now());
                tenant = tenantRepository.save(tenant);
                log.info("Tenant auto-activated: {}", tenant.getId());
            }

            log.info("Tenant provisioning completed successfully for: {}", tenant.getName());
            return tenant;

        } catch (Exception e) {
            log.error("Error provisioning tenant: {}", tenant.getName(), e);
            throw new TenantProvisioningException("Failed to provision tenant", e);
        }
    }

    /**
     * Provisions data resources (database/schema) for the tenant.
     */
    private void provisionDataResources(Tenant tenant) {
        switch (tenant.getIsolationStrategy()) {
            case DATABASE_PER_TENANT -> provisionDatabase(tenant);
            case SCHEMA_PER_TENANT, HYBRID_POOL -> provisionSchema(tenant);
            case SHARED_SCHEMA_ROW_LEVEL, SHARED_SCHEMA_BASIC -> {
                // No provisioning needed for shared schema
                log.info("Using shared schema for tenant: {}", tenant.getId());
            }
        }
    }

    /**
     * Provisions a dedicated database for the tenant.
     */
    private void provisionDatabase(Tenant tenant) {
        try {
            String dbName = tenant.getDatabaseName();
            log.info("Provisioning database: {} for tenant: {}", dbName, tenant.getId());

            // Note: Database creation typically requires superuser privileges
            // and cannot be done in a transaction. This would typically be
            // handled by an external provisioning service or script.

            // For now, we'll create the data source configuration
            String jdbcUrl = String.format("jdbc:postgresql://%s:%d/%s",
                    tenant.getDatabaseServer(),
                    tenant.getDatabasePort(),
                    dbName
            );

            // Create tenant-specific data source
            DataSource tenantDataSource = dataSourceConfig.createTenantDataSource(
                    tenant.getId().toString(),
                    jdbcUrl,
                    "tenant_" + tenant.getId(), // username
                    generateSecurePassword(),    // password
                    tenant.getConnectionPoolSize()
            );

            log.info("Database provisioned and data source created for tenant: {}", tenant.getId());

        } catch (Exception e) {
            log.error("Error provisioning database for tenant: {}", tenant.getId(), e);
            throw new TenantProvisioningException("Failed to provision database", e);
        }
    }

    /**
     * Provisions a schema for the tenant.
     */
    private void provisionSchema(Tenant tenant) {
        try {
            String schemaName = tenant.getSchemaName();
            log.info("Provisioning schema: {} for tenant: {}", schemaName, tenant.getId());

            // Create schema
            schemaService.createSchema(schemaName);

            // Initialize schema with tenant-specific tables
            schemaService.setSchema(schemaName);

            // Run Flyway migrations for tenant schema (if needed)
            // This would typically be handled by a separate migration service

            log.info("Schema provisioned for tenant: {}", tenant.getId());

        } catch (Exception e) {
            log.error("Error provisioning schema for tenant: {}", tenant.getId(), e);
            throw new TenantProvisioningException("Failed to provision schema", e);
        } finally {
            // Reset to public schema
            schemaService.clearSchema();
        }
    }

    /**
     * Determines the appropriate isolation strategy based on organization type.
     */
    private TenantIsolationStrategy determineIsolationStrategy(
            OrganizationType orgType, boolean hasCompliance) {

        // Government and financial always get database isolation
        if (orgType == OrganizationType.GOVERNMENT_AGENCY ||
                orgType == OrganizationType.FINANCIAL_INSTITUTION) {
            return TenantIsolationStrategy.DATABASE_PER_TENANT;
        }

        // Healthcare gets database or schema isolation
        if (orgType == OrganizationType.HEALTHCARE) {
            return hasCompliance ?
                    TenantIsolationStrategy.DATABASE_PER_TENANT :
                    TenantIsolationStrategy.SCHEMA_PER_TENANT;
        }

        // Large organizations get schema isolation
        if (orgType == OrganizationType.CORPORATION ||
                orgType == OrganizationType.ACADEMIC_INSTITUTION) {
            return TenantIsolationStrategy.SCHEMA_PER_TENANT;
        }

        // Small organizations get row-level isolation
        if (orgType == OrganizationType.RESEARCH_ORGANIZATION ||
                orgType == OrganizationType.NON_PROFIT ||
                orgType == OrganizationType.STARTUP) {
            return TenantIsolationStrategy.SHARED_SCHEMA_ROW_LEVEL;
        }

        // Individuals get basic shared schema
        if (orgType == OrganizationType.INDIVIDUAL) {
            return TenantIsolationStrategy.SHARED_SCHEMA_BASIC;
        }

        // Default to row-level security
        return TenantIsolationStrategy.SHARED_SCHEMA_ROW_LEVEL;
    }

    /**
     * Determines the connection pool size based on organization type and plan.
     */
    private int determinePoolSize(Tenant tenant) {
        return switch (tenant.getOrganizationType()) {
            case GOVERNMENT_AGENCY, FINANCIAL_INSTITUTION -> 50;
            case CORPORATION, HEALTHCARE -> 30;
            case ACADEMIC_INSTITUTION -> 25;
            case RESEARCH_ORGANIZATION -> 15;
            case NON_PROFIT, STARTUP -> 10;
            case INDIVIDUAL -> 5;
        };
    }

    /**
     * Determines the database server for the tenant based on region.
     */
    private String getDatabaseServer(Tenant tenant) {
        // This would typically be determined by data residency requirements
        String region = tenant.getDataResidencyRegion();
        if (region != null) {
            return switch (region) {
                case "US-EAST" -> "db-us-east.nnipa.cloud";
                case "US-WEST" -> "db-us-west.nnipa.cloud";
                case "EU" -> "db-eu.nnipa.cloud";
                case "ASIA" -> "db-asia.nnipa.cloud";
                default -> "db-default.nnipa.cloud";
            };
        }
        return "localhost"; // Default for development
    }

    /**
     * Sanitizes name for use in database/schema names.
     */
    private String sanitizeName(String name) {
        return name.replaceAll("[^a-zA-Z0-9_]", "_")
                .replaceAll("_{2,}", "_")
                .toLowerCase();
    }

    /**
     * Generates a secure password for database access.
     */
    private String generateSecurePassword() {
        // In production, use a secure password generator
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * Determines if tenant should be auto-activated.
     */
    private boolean shouldAutoActivate(Tenant tenant) {
        // Auto-activate for certain organization types in development
        return tenant.getOrganizationType() == OrganizationType.INDIVIDUAL ||
                tenant.getStatus() == TenantStatus.TRIAL;
    }

    /**
     * Deprovisions a tenant's data resources.
     */
    @Transactional
    public void deprovisionTenant(Tenant tenant) {
        try {
            log.info("Starting tenant deprovisioning for: {}", tenant.getName());

            switch (tenant.getIsolationStrategy()) {
                case DATABASE_PER_TENANT -> {
                    // Remove data source from pool
                    dataSourceConfig.removeTenantDataSource(tenant.getId().toString());
                    log.info("Removed data source for tenant: {}", tenant.getId());
                    // Note: Actual database deletion would be handled externally
                }
                case SCHEMA_PER_TENANT, HYBRID_POOL -> {
                    // Drop schema (with caution)
                    if (tenant.getSchemaName() != null) {
                        schemaService.dropSchema(tenant.getSchemaName());
                        log.info("Dropped schema for tenant: {}", tenant.getId());
                    }
                }
                case SHARED_SCHEMA_ROW_LEVEL, SHARED_SCHEMA_BASIC -> {
                    // Data remains in shared tables, marked as deleted via soft delete
                    log.info("Tenant data retained in shared schema (soft delete): {}", tenant.getId());
                }
            }

            // Update tenant status
            tenant.setStatus(TenantStatus.DELETED);
            tenant.softDelete("SYSTEM");
            tenantRepository.save(tenant);

            log.info("Tenant deprovisioning completed for: {}", tenant.getName());

        } catch (Exception e) {
            log.error("Error deprovisioning tenant: {}", tenant.getName(), e);
            throw new TenantProvisioningException("Failed to deprovision tenant", e);
        }
    }

    /**
     * Migrates a tenant to a different isolation strategy.
     */
    @Transactional
    public void migrateTenantIsolation(Tenant tenant, TenantIsolationStrategy newStrategy) {
        try {
            log.info("Migrating tenant {} from {} to {}",
                    tenant.getName(), tenant.getIsolationStrategy(), newStrategy);

            TenantIsolationStrategy oldStrategy = tenant.getIsolationStrategy();

            // Step 1: Set tenant to migrating status
            tenant.setStatus(TenantStatus.MIGRATING);
            tenantRepository.save(tenant);

            // Step 2: Provision new resources
            tenant.setIsolationStrategy(newStrategy);
            provisionDataResources(tenant);

            // Step 3: Migrate data (would typically be handled by ETL process)
            // This is a placeholder - actual implementation would involve data migration
            log.info("Data migration would occur here from {} to {}", oldStrategy, newStrategy);

            // Step 4: Clean up old resources
            if (oldStrategy == TenantIsolationStrategy.SCHEMA_PER_TENANT &&
                    tenant.getSchemaName() != null) {
                // Schedule cleanup after data verification
                log.info("Old schema {} marked for cleanup", tenant.getSchemaName());
            }

            // Step 5: Update tenant status
            tenant.setStatus(TenantStatus.ACTIVE);
            tenant.setIsolationStrategy(newStrategy);
            tenantRepository.save(tenant);

            log.info("Tenant isolation migration completed for: {}", tenant.getName());

        } catch (Exception e) {
            log.error("Error migrating tenant isolation: {}", tenant.getName(), e);
            tenant.setStatus(TenantStatus.ACTIVE); // Rollback to active
            tenantRepository.save(tenant);
            throw new TenantProvisioningException("Failed to migrate tenant isolation", e);
        }
    }

    /**
     * Custom exception for provisioning errors.
     */
    public static class TenantProvisioningException extends RuntimeException {
        public TenantProvisioningException(String message) {
            super(message);
        }

        public TenantProvisioningException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}