package com.nnipa.tenant.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Tenant data isolation strategies for multi-tenant architecture.
 * Different strategies based on security, compliance, and performance requirements.
 */
@Getter
@RequiredArgsConstructor
public enum TenantIsolationStrategy {

    DATABASE_PER_TENANT(
            "Database Per Tenant",
            "Complete database isolation with separate database instance per tenant",
            true,
            true,
            100,
            "CREATE DATABASE {tenant_db}",
            true
    ),

    SCHEMA_PER_TENANT(
            "Schema Per Tenant",
            "Logical isolation with separate schema per tenant in shared database",
            true,
            false,
            80,
            "CREATE SCHEMA {tenant_schema}",
            true
    ),

    SHARED_SCHEMA_ROW_LEVEL(
            "Shared Schema with Row-Level Security",
            "Shared tables with tenant_id discrimination and row-level security policies",
            false,
            false,
            60,
            null,
            false
    ),

    HYBRID_POOL(
            "Hybrid Pool",
            "Pool of databases with multiple tenants per database but isolated schemas",
            true,
            false,
            70,
            "CREATE SCHEMA {tenant_schema}",
            true
    ),

    SHARED_SCHEMA_BASIC(
            "Shared Schema Basic",
            "Shared tables with tenant_id column, no row-level security",
            false,
            false,
            40,
            null,
            false
    );

    private final String displayName;
    private final String description;
    private final boolean providesSchemaIsolation;
    private final boolean providesDatabaseIsolation;
    private final int isolationScore; // 0-100, higher is better isolation
    private final String schemaCreationTemplate;
    private final boolean supportsDynamicProvisioning;

    /**
     * Determines if this strategy is suitable for an organization type.
     */
    public boolean isSuitableFor(OrganizationType orgType) {
        return switch (orgType) {
            case GOVERNMENT_AGENCY, FINANCIAL_INSTITUTION ->
                    this == DATABASE_PER_TENANT;
            case HEALTHCARE ->
                    this == DATABASE_PER_TENANT || this == SCHEMA_PER_TENANT;
            case CORPORATION, ACADEMIC_INSTITUTION ->
                    this == SCHEMA_PER_TENANT || this == HYBRID_POOL;
            case RESEARCH_ORGANIZATION, NON_PROFIT, STARTUP ->
                    this == SCHEMA_PER_TENANT || this == SHARED_SCHEMA_ROW_LEVEL;
            case INDIVIDUAL ->
                    this == SHARED_SCHEMA_ROW_LEVEL || this == SHARED_SCHEMA_BASIC;
        };
    }

    /**
     * Gets the connection pool strategy for this isolation level.
     */
    public String getConnectionPoolStrategy() {
        return switch (this) {
            case DATABASE_PER_TENANT -> "TENANT_CONNECTION_POOL";
            case SCHEMA_PER_TENANT, HYBRID_POOL -> "SHARED_POOL_WITH_TENANT_CONTEXT";
            case SHARED_SCHEMA_ROW_LEVEL, SHARED_SCHEMA_BASIC -> "SHARED_CONNECTION_POOL";
        };
    }

    /**
     * Determines if this strategy requires dynamic datasource routing.
     */
    public boolean requiresDynamicRouting() {
        return this == DATABASE_PER_TENANT || this == HYBRID_POOL;
    }

    /**
     * Gets the recommended backup strategy.
     */
    public String getBackupStrategy() {
        return switch (this) {
            case DATABASE_PER_TENANT -> "INDIVIDUAL_DATABASE_BACKUP";
            case SCHEMA_PER_TENANT -> "SCHEMA_LEVEL_BACKUP";
            case HYBRID_POOL -> "DATABASE_POOL_BACKUP";
            case SHARED_SCHEMA_ROW_LEVEL, SHARED_SCHEMA_BASIC -> "TABLE_LEVEL_TENANT_BACKUP";
        };
    }
}