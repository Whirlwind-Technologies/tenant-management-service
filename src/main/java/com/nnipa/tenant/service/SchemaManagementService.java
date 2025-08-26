package com.nnipa.tenant.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;

/**
 * Service for managing database schemas in multi-tenant architecture.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SchemaManagementService {

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
            validateSchemaName(schemaName);
            String sql = String.format("SET search_path TO %s, public", schemaName);
            jdbcTemplate.execute(sql);
            log.debug("Schema set to: {}", schemaName);
        } catch (Exception e) {
            log.error("Error setting schema: {}", schemaName, e);
            throw new SchemaException("Failed to set schema: " + schemaName, e);
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
     * Creates a new schema for a tenant.
     */
    @Transactional
    public void createSchema(String schemaName) {
        try {
            validateSchemaName(schemaName);

            String checkSql = "SELECT schema_name FROM information_schema.schemata WHERE schema_name = ?";
            var schemas = jdbcTemplate.queryForList(checkSql, String.class, schemaName);

            if (schemas.isEmpty()) {
                String createSql = String.format("CREATE SCHEMA %s", schemaName);
                jdbcTemplate.execute(createSql);

                String grantSql = String.format("GRANT ALL ON SCHEMA %s TO CURRENT_USER", schemaName);
                jdbcTemplate.execute(grantSql);

                log.info("Created schema: {}", schemaName);
                createTenantTables(schemaName);
            } else {
                log.info("Schema already exists: {}", schemaName);
            }
        } catch (Exception e) {
            log.error("Error creating schema: {}", schemaName, e);
            throw new SchemaException("Failed to create schema: " + schemaName, e);
        }
    }

    private void createTenantTables(String schemaName) {
        // Implementation for creating tenant-specific tables
        // This would create the statistical data tables as shown in the original
    }

    private void validateSchemaName(String schemaName) {
        if (schemaName == null || schemaName.isBlank()) {
            throw new IllegalArgumentException("Schema name cannot be null or empty");
        }

        if (!schemaName.matches("^[a-zA-Z0-9_]+$")) {
            throw new IllegalArgumentException("Invalid schema name: " + schemaName);
        }

        if (schemaName.length() > 63) {
            throw new IllegalArgumentException("Schema name too long: " + schemaName);
        }
    }

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

    public static class SchemaException extends RuntimeException {
        public SchemaException(String message) {
            super(message);
        }

        public SchemaException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}