package com.nnipa.tenant.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Service for managing database schemas in multi-tenant architecture.
 * Handles schema switching for SCHEMA_PER_TENANT strategy and
 * row-level security for SHARED_SCHEMA strategies.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SchemaService {

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    /**
     * Sets the current schema for the database session.
     */
    @Transactional
    public void setSchema(String schemaName) {
        if (schemaName == null || schemaName.isBlank()) {
            log.warn("Attempted to set null or empty schema");
            return;
        }

        try {
            // Validate schema name to prevent SQL injection
            validateSchemaName(schemaName);

            // Set search_path for PostgreSQL
            String sql = String.format("SET search_path TO %s, public", schemaName);
            jdbcTemplate.execute(sql);

            log.debug("Schema set to: {}", schemaName);
        } catch (Exception e) {
            log.error("Error setting schema: {}", schemaName, e);
            throw new SchemaException("Failed to set schema: " + schemaName, e);
        }
    }

    /**
     * Creates a new schema for a tenant.
     */
    @Transactional
    public void createSchema(String schemaName) {
        try {
            validateSchemaName(schemaName);

            // Check if schema exists
            String checkSql = "SELECT schema_name FROM information_schema.schemata WHERE schema_name = ?";
            var schemas = jdbcTemplate.queryForList(checkSql, String.class, schemaName);

            if (schemas.isEmpty()) {
                // Create schema
                String createSql = String.format("CREATE SCHEMA %s", schemaName);
                jdbcTemplate.execute(createSql);

                // Grant permissions
                String grantSql = String.format("GRANT ALL ON SCHEMA %s TO CURRENT_USER", schemaName);
                jdbcTemplate.execute(grantSql);

                log.info("Created schema: {}", schemaName);

                // Create tenant-specific tables
                createTenantTables(schemaName);
            } else {
                log.info("Schema already exists: {}", schemaName);
            }
        } catch (Exception e) {
            log.error("Error creating schema: {}", schemaName, e);
            throw new SchemaException("Failed to create schema: " + schemaName, e);
        }
    }

    /**
     * Creates tenant-specific tables in the schema.
     */
    private void createTenantTables(String schemaName) {
        try {
            // Set schema context
            setSchema(schemaName);

            // Create datasets table
            String createDatasetsTable = """
                CREATE TABLE IF NOT EXISTS datasets (
                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                    name VARCHAR(255) NOT NULL,
                    description TEXT,
                    data_type VARCHAR(50),
                    source VARCHAR(255),
                    size_bytes BIGINT,
                    row_count BIGINT,
                    column_count INTEGER,
                    metadata JSONB,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP,
                    created_by VARCHAR(255),
                    is_public BOOLEAN DEFAULT FALSE
                )
            """;
            jdbcTemplate.execute(createDatasetsTable);

            // Create analyses table
            String createAnalysesTable = """
                CREATE TABLE IF NOT EXISTS statistical_analyses (
                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                    dataset_id UUID REFERENCES datasets(id),
                    analysis_type VARCHAR(100) NOT NULL,
                    analysis_name VARCHAR(255),
                    description TEXT,
                    parameters JSONB,
                    results JSONB,
                    status VARCHAR(30),
                    started_at TIMESTAMP,
                    completed_at TIMESTAMP,
                    error_message TEXT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    created_by VARCHAR(255)
                )
            """;
            jdbcTemplate.execute(createAnalysesTable);

            // Create reports table
            String createReportsTable = """
                CREATE TABLE IF NOT EXISTS reports (
                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                    analysis_id UUID REFERENCES statistical_analyses(id),
                    report_name VARCHAR(255) NOT NULL,
                    report_type VARCHAR(50),
                    format VARCHAR(20),
                    content TEXT,
                    file_path VARCHAR(500),
                    metadata JSONB,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    created_by VARCHAR(255)
                )
            """;
            jdbcTemplate.execute(createReportsTable);

            // Create indexes
            jdbcTemplate.execute("CREATE INDEX idx_datasets_name ON datasets(name)");
            jdbcTemplate.execute("CREATE INDEX idx_analyses_type ON statistical_analyses(analysis_type)");
            jdbcTemplate.execute("CREATE INDEX idx_analyses_status ON statistical_analyses(status)");
            jdbcTemplate.execute("CREATE INDEX idx_reports_analysis ON reports(analysis_id)");

            log.info("Created tenant tables in schema: {}", schemaName);

        } catch (Exception e) {
            log.error("Error creating tenant tables in schema: {}", schemaName, e);
            throw new SchemaException("Failed to create tenant tables", e);
        }
    }

    /**
     * Drops a schema (use with caution).
     */
    @Transactional
    public void dropSchema(String schemaName) {
        try {
            validateSchemaName(schemaName);

            // Safety check - don't drop public or system schemas
            if ("public".equalsIgnoreCase(schemaName) ||
                    schemaName.startsWith("pg_") ||
                    "information_schema".equalsIgnoreCase(schemaName)) {
                throw new IllegalArgumentException("Cannot drop system schema: " + schemaName);
            }

            String dropSql = String.format("DROP SCHEMA IF EXISTS %s CASCADE", schemaName);
            jdbcTemplate.execute(dropSql);

            log.info("Dropped schema: {}", schemaName);
        } catch (Exception e) {
            log.error("Error dropping schema: {}", schemaName, e);
            throw new SchemaException("Failed to drop schema: " + schemaName, e);
        }
    }

    /**
     * Sets tenant ID for row-level security in shared schema.
     */
    public void setTenantIdForRLS(String tenantId) {
        try {
            if (tenantId == null || tenantId.isBlank()) {
                log.warn("Attempted to set null or empty tenant ID for RLS");
                return;
            }

            // Set configuration parameter for row-level security
            String sql = "SELECT set_config('app.current_tenant_id', ?, false)";
            jdbcTemplate.queryForObject(sql, String.class, tenantId);

            log.debug("Set tenant ID for RLS: {}", tenantId);
        } catch (Exception e) {
            log.error("Error setting tenant ID for RLS: {}", tenantId, e);
            throw new SchemaException("Failed to set tenant ID for RLS", e);
        }
    }

    /**
     * Clears the schema setting (resets to public).
     */
    public void clearSchema() {
        try {
            jdbcTemplate.execute("SET search_path TO public");
            log.debug("Schema reset to public");
        } catch (Exception e) {
            log.error("Error clearing schema", e);
        }
    }

    /**
     * Validates schema name to prevent SQL injection.
     */
    private void validateSchemaName(String schemaName) {
        if (schemaName == null || schemaName.isBlank()) {
            throw new IllegalArgumentException("Schema name cannot be null or empty");
        }

        // Only allow alphanumeric characters and underscores
        if (!schemaName.matches("^[a-zA-Z0-9_]+$")) {
            throw new IllegalArgumentException("Invalid schema name: " + schemaName);
        }

        // Check length
        if (schemaName.length() > 63) { // PostgreSQL identifier limit
            throw new IllegalArgumentException("Schema name too long: " + schemaName);
        }
    }

    /**
     * Gets the current schema from the database connection.
     */
    public String getCurrentSchema() {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT current_schema()",
                    String.class
            );
        } catch (Exception e) {
            log.error("Error getting current schema", e);
            return "public";
        }
    }

    /**
     * Checks if a schema exists.
     */
    public boolean schemaExists(String schemaName) {
        try {
            validateSchemaName(schemaName);
            String sql = "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = ?";
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, schemaName);
            return count != null && count > 0;
        } catch (Exception e) {
            log.error("Error checking schema existence: {}", schemaName, e);
            return false;
        }
    }

    /**
     * Custom exception for schema operations.
     */
    public static class SchemaException extends RuntimeException {
        public SchemaException(String message) {
            super(message);
        }

        public SchemaException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}