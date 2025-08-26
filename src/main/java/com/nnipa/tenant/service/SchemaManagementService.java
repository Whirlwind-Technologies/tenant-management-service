package com.nnipa.tenant.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import javax.sql.DataSource;


/**
 * Service for managing database schemas in multi-tenant architecture.
 * Handles schema creation, deletion, and management for different isolation strategies.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SchemaManagementService {

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    @Value("${app.tenant.schema.prefix:tenant_}")
    private String schemaPrefix;

    @Value("${app.tenant.schema.template:tenant_template}")
    private String templateSchema;

    private static final ThreadLocal<String> currentSchema = new ThreadLocal<>();

    /**
     * Creates a new schema for tenant with SCHEMA_PER_TENANT or HYBRID_POOL strategy.
     */
    @Transactional
    public void createSchema(String schemaName) {
        log.info("Creating schema: {}", schemaName);

        try {
            // Validate schema name
            validateSchemaName(schemaName);

            // Check if schema already exists
            if (schemaExists(schemaName)) {
                log.warn("Schema {} already exists, skipping creation", schemaName);
                return;
            }

            // Create schema
            String createSchemaSql = String.format("CREATE SCHEMA IF NOT EXISTS %s AUTHORIZATION CURRENT_USER",
                    sanitizeIdentifier(schemaName));
            jdbcTemplate.execute(createSchemaSql);
            log.debug("Schema created: {}", schemaName);

            // Grant permissions
            grantSchemaPermissions(schemaName);

            // Create tenant-specific tables in the schema
            createTenantTables(schemaName);

            // Create indexes
            createSchemaIndexes(schemaName);

            // Create views if needed
            createSchemaViews(schemaName);

            log.info("Schema {} created and initialized successfully", schemaName);

        } catch (Exception e) {
            log.error("Failed to create schema: {}", schemaName, e);
            throw new SchemaManagementException("Failed to create schema: " + schemaName, e);
        }
    }

    /**
     * Creates tenant-specific tables in the schema.
     */
    private void createTenantTables(String schemaName) {
        log.debug("Creating tables in schema: {}", schemaName);

        try {
            // Set search path to the new schema
            setSchema(schemaName);

            // Create datasets table
            String createDatasetsTable = """
                CREATE TABLE IF NOT EXISTS %s.datasets (
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
                    is_public BOOLEAN DEFAULT FALSE,
                    CONSTRAINT uk_dataset_name UNIQUE (name)
                )
                """.formatted(sanitizeIdentifier(schemaName));

            jdbcTemplate.execute(createDatasetsTable);

            // Create statistical_analyses table
            String createAnalysesTable = """
                CREATE TABLE IF NOT EXISTS %s.statistical_analyses (
                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                    dataset_id UUID REFERENCES %s.datasets(id) ON DELETE CASCADE,
                    analysis_type VARCHAR(100) NOT NULL,
                    analysis_name VARCHAR(255),
                    description TEXT,
                    parameters JSONB,
                    results JSONB,
                    status VARCHAR(30),
                    started_at TIMESTAMP,
                    completed_at TIMESTAMP,
                    error_message TEXT,
                    execution_time_ms BIGINT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    created_by VARCHAR(255),
                    CONSTRAINT uk_analysis_name UNIQUE (analysis_name)
                )
                """.formatted(sanitizeIdentifier(schemaName), sanitizeIdentifier(schemaName));

            jdbcTemplate.execute(createAnalysesTable);

            // Create reports table
            String createReportsTable = """
                CREATE TABLE IF NOT EXISTS %s.reports (
                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                    analysis_id UUID REFERENCES %s.statistical_analyses(id) ON DELETE CASCADE,
                    report_name VARCHAR(255) NOT NULL,
                    report_type VARCHAR(50),
                    format VARCHAR(20),
                    content TEXT,
                    file_path VARCHAR(500),
                    metadata JSONB,
                    generated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    generated_by VARCHAR(255),
                    is_published BOOLEAN DEFAULT FALSE,
                    published_at TIMESTAMP,
                    access_count INTEGER DEFAULT 0,
                    CONSTRAINT uk_report_name UNIQUE (report_name)
                )
                """.formatted(sanitizeIdentifier(schemaName), sanitizeIdentifier(schemaName));

            jdbcTemplate.execute(createReportsTable);

            // Create data_imports table for tracking imports
            String createImportsTable = """
                CREATE TABLE IF NOT EXISTS %s.data_imports (
                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                    dataset_id UUID REFERENCES %s.datasets(id) ON DELETE CASCADE,
                    import_type VARCHAR(50),
                    source_url VARCHAR(500),
                    status VARCHAR(30),
                    total_records BIGINT,
                    processed_records BIGINT,
                    failed_records BIGINT,
                    error_log TEXT,
                    started_at TIMESTAMP,
                    completed_at TIMESTAMP,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """.formatted(sanitizeIdentifier(schemaName), sanitizeIdentifier(schemaName));

            jdbcTemplate.execute(createImportsTable);

            log.debug("Tables created successfully in schema: {}", schemaName);

        } catch (Exception e) {
            log.error("Failed to create tables in schema: {}", schemaName, e);
            throw new SchemaManagementException("Failed to create tables in schema: " + schemaName, e);
        } finally {
            clearSchema();
        }
    }

    /**
     * Creates indexes for better performance.
     */
    private void createSchemaIndexes(String schemaName) {
        log.debug("Creating indexes in schema: {}", schemaName);

        try {
            // Indexes for datasets
            jdbcTemplate.execute(String.format(
                    "CREATE INDEX IF NOT EXISTS idx_%s_datasets_created_at ON %s.datasets(created_at DESC)",
                    schemaName.replace("-", "_"), sanitizeIdentifier(schemaName)));

            jdbcTemplate.execute(String.format(
                    "CREATE INDEX IF NOT EXISTS idx_%s_datasets_data_type ON %s.datasets(data_type)",
                    schemaName.replace("-", "_"), sanitizeIdentifier(schemaName)));

            // Indexes for analyses
            jdbcTemplate.execute(String.format(
                    "CREATE INDEX IF NOT EXISTS idx_%s_analyses_dataset_id ON %s.statistical_analyses(dataset_id)",
                    schemaName.replace("-", "_"), sanitizeIdentifier(schemaName)));

            jdbcTemplate.execute(String.format(
                    "CREATE INDEX IF NOT EXISTS idx_%s_analyses_status ON %s.statistical_analyses(status)",
                    schemaName.replace("-", "_"), sanitizeIdentifier(schemaName)));

            jdbcTemplate.execute(String.format(
                    "CREATE INDEX IF NOT EXISTS idx_%s_analyses_created_at ON %s.statistical_analyses(created_at DESC)",
                    schemaName.replace("-", "_"), sanitizeIdentifier(schemaName)));

            // Indexes for reports
            jdbcTemplate.execute(String.format(
                    "CREATE INDEX IF NOT EXISTS idx_%s_reports_analysis_id ON %s.reports(analysis_id)",
                    schemaName.replace("-", "_"), sanitizeIdentifier(schemaName)));

            jdbcTemplate.execute(String.format(
                    "CREATE INDEX IF NOT EXISTS idx_%s_reports_published ON %s.reports(is_published, published_at)",
                    schemaName.replace("-", "_"), sanitizeIdentifier(schemaName)));

            log.debug("Indexes created successfully in schema: {}", schemaName);

        } catch (Exception e) {
            log.error("Failed to create indexes in schema: {}", schemaName, e);
            // Non-critical, don't throw exception
        }
    }

    /**
     * Creates views for common queries.
     */
    private void createSchemaViews(String schemaName) {
        log.debug("Creating views in schema: {}", schemaName);

        try {
            // Create view for active analyses
            String activeAnalysesView = """
                CREATE OR REPLACE VIEW %s.v_active_analyses AS
                SELECT 
                    a.id,
                    a.analysis_name,
                    a.analysis_type,
                    a.status,
                    a.started_at,
                    d.name as dataset_name,
                    d.row_count as dataset_rows
                FROM %s.statistical_analyses a
                JOIN %s.datasets d ON a.dataset_id = d.id
                WHERE a.status IN ('RUNNING', 'QUEUED', 'PENDING')
                """.formatted(sanitizeIdentifier(schemaName),
                    sanitizeIdentifier(schemaName),
                    sanitizeIdentifier(schemaName));

            jdbcTemplate.execute(activeAnalysesView);

            // Create view for published reports
            String publishedReportsView = """
                CREATE OR REPLACE VIEW %s.v_published_reports AS
                SELECT 
                    r.id,
                    r.report_name,
                    r.report_type,
                    r.format,
                    r.published_at,
                    r.access_count,
                    a.analysis_name,
                    d.name as dataset_name
                FROM %s.reports r
                JOIN %s.statistical_analyses a ON r.analysis_id = a.id
                JOIN %s.datasets d ON a.dataset_id = d.id
                WHERE r.is_published = true
                """.formatted(sanitizeIdentifier(schemaName),
                    sanitizeIdentifier(schemaName),
                    sanitizeIdentifier(schemaName),
                    sanitizeIdentifier(schemaName));

            jdbcTemplate.execute(publishedReportsView);

            log.debug("Views created successfully in schema: {}", schemaName);

        } catch (Exception e) {
            log.error("Failed to create views in schema: {}", schemaName, e);
            // Non-critical, don't throw exception
        }
    }

    /**
     * Grants appropriate permissions on the schema.
     */
    private void grantSchemaPermissions(String schemaName) {
        try {
            // Grant usage on schema
            String grantUsage = String.format("GRANT USAGE ON SCHEMA %s TO PUBLIC",
                    sanitizeIdentifier(schemaName));
            jdbcTemplate.execute(grantUsage);

            // Grant create permission to schema owner role if exists
            String grantCreate = String.format("GRANT CREATE ON SCHEMA %s TO CURRENT_USER",
                    sanitizeIdentifier(schemaName));
            jdbcTemplate.execute(grantCreate);

            log.debug("Permissions granted on schema: {}", schemaName);

        } catch (Exception e) {
            log.warn("Failed to grant permissions on schema: {}", schemaName, e);
            // Non-critical, continue
        }
    }

    /**
     * Drops a schema and all its contents.
     */
    @Transactional
    public void dropSchema(String schemaName) {
        log.warn("Dropping schema: {}", schemaName);

        try {
            validateSchemaName(schemaName);

            if (!schemaExists(schemaName)) {
                log.warn("Schema {} does not exist, skipping deletion", schemaName);
                return;
            }

            // Drop schema cascade
            String dropSchemaSql = String.format("DROP SCHEMA IF EXISTS %s CASCADE",
                    sanitizeIdentifier(schemaName));
            jdbcTemplate.execute(dropSchemaSql);

            log.info("Schema {} dropped successfully", schemaName);

        } catch (Exception e) {
            log.error("Failed to drop schema: {}", schemaName, e);
            throw new SchemaManagementException("Failed to drop schema: " + schemaName, e);
        }
    }

    /**
     * Checks if a schema exists.
     */
    public boolean schemaExists(String schemaName) {
        try {
            String sql = "SELECT EXISTS (SELECT 1 FROM information_schema.schemata WHERE schema_name = ?)";
            Boolean exists = jdbcTemplate.queryForObject(sql, Boolean.class, schemaName.toLowerCase());
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            log.error("Error checking schema existence: {}", schemaName, e);
            return false;
        }
    }

    /**
     * Sets the current schema for the connection.
     */
    public void setSchema(String schemaName) {
        if (schemaName != null) {
            try {
                String sql = String.format("SET search_path TO %s, public",
                        sanitizeIdentifier(schemaName));
                jdbcTemplate.execute(sql);
                currentSchema.set(schemaName);
                log.debug("Schema set to: {}", schemaName);
            } catch (Exception e) {
                log.error("Failed to set schema: {}", schemaName, e);
                throw new SchemaManagementException("Failed to set schema: " + schemaName, e);
            }
        }
    }

    /**
     * Clears the current schema setting.
     */
    public void clearSchema() {
        try {
            jdbcTemplate.execute("SET search_path TO public");
            currentSchema.remove();
            log.debug("Schema cleared, reset to public");
        } catch (Exception e) {
            log.error("Failed to clear schema", e);
        }
    }

    /**
     * Sets tenant ID for row-level security.
     */
    public void setTenantIdForRLS(String tenantId) {
        try {
            String sql = String.format("SET LOCAL app.current_tenant_id = '%s'",
                    sanitizeValue(tenantId));
            jdbcTemplate.execute(sql);
            log.debug("Tenant ID set for RLS: {}", tenantId);
        } catch (Exception e) {
            log.error("Failed to set tenant ID for RLS: {}", tenantId, e);
        }
    }

    /**
     * Gets the count of tables in a schema.
     */
    public int getTableCount(String schemaName) {
        try {
            String sql = """
                SELECT COUNT(*) 
                FROM information_schema.tables 
                WHERE table_schema = ? 
                AND table_type = 'BASE TABLE'
                """;
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, schemaName.toLowerCase());
            return count != null ? count : 0;
        } catch (Exception e) {
            log.error("Error getting table count for schema: {}", schemaName, e);
            return 0;
        }
    }

    /**
     * Validates schema name to prevent SQL injection.
     */
    private void validateSchemaName(String schemaName) {
        if (schemaName == null || schemaName.trim().isEmpty()) {
            throw new IllegalArgumentException("Schema name cannot be null or empty");
        }

        // Only allow alphanumeric, underscore, and dash
        if (!schemaName.matches("^[a-zA-Z0-9_-]+$")) {
            throw new IllegalArgumentException("Invalid schema name: " + schemaName);
        }

        // Check length
        if (schemaName.length() > 63) { // PostgreSQL identifier limit
            throw new IllegalArgumentException("Schema name too long: " + schemaName);
        }

        // Prevent reserved words
        if (isReservedWord(schemaName)) {
            throw new IllegalArgumentException("Schema name is a reserved word: " + schemaName);
        }
    }

    /**
     * Checks if a name is a PostgreSQL reserved word.
     */
    private boolean isReservedWord(String name) {
        String[] reservedWords = {
                "public", "information_schema", "pg_catalog", "pg_toast",
                "user", "role", "database", "schema", "table", "column"
        };

        String lowerName = name.toLowerCase();
        for (String reserved : reservedWords) {
            if (reserved.equals(lowerName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Sanitizes an identifier to prevent SQL injection.
     */
    private String sanitizeIdentifier(String identifier) {
        // Remove any quotes and escape properly
        return "\"" + identifier.replace("\"", "") + "\"";
    }

    /**
     * Sanitizes a value to prevent SQL injection.
     */
    private String sanitizeValue(String value) {
        return value.replace("'", "''");
    }

    /**
     * Custom exception for schema management operations.
     */
    public static class SchemaManagementException extends RuntimeException {
        public SchemaManagementException(String message) {
            super(message);
        }

        public SchemaManagementException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}