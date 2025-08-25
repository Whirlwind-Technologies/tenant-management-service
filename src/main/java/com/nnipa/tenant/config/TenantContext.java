package com.nnipa.tenant.config;

import com.nnipa.tenant.enums.TenantIsolationStrategy;
import lombok.extern.slf4j.Slf4j;

/**
 * Thread-local storage for current tenant context.
 * Used for multi-tenant data source routing and row-level security.
 */
@Slf4j
public class TenantContext {

    private static final ThreadLocal<String> currentTenant = new ThreadLocal<>();
    private static final ThreadLocal<String> currentSchema = new ThreadLocal<>();
    private static final ThreadLocal<TenantIsolationStrategy> isolationStrategy = new ThreadLocal<>();
    private static final ThreadLocal<String> tenantDatabase = new ThreadLocal<>();
    private static final ThreadLocal<String> tenantSchema = new ThreadLocal<>();

    /**
     * Sets the current tenant context.
     */
    public static void setCurrentTenant(String tenantId) {
        log.debug("Setting current tenant: {}", tenantId);
        currentTenant.set(tenantId);
    }

    /**
     * Gets the current tenant ID.
     */
    public static String getCurrentTenant() {
        return currentTenant.get();
    }

    /**
     * Sets the current schema for schema-per-tenant strategies.
     */
    public static void setCurrentSchema(String schema) {
        log.debug("Setting current schema: {}", schema);
        currentSchema.set(schema);
    }

    /**
     * Gets the current schema.
     */
    public static String getCurrentSchema() {
        return currentSchema.get();
    }

    /**
     * Sets the isolation strategy for the current tenant.
     */
    public static void setIsolationStrategy(TenantIsolationStrategy strategy) {
        log.debug("Setting isolation strategy: {}", strategy);
        isolationStrategy.set(strategy);
    }

    /**
     * Gets the isolation strategy for the current tenant.
     */
    public static TenantIsolationStrategy getIsolationStrategy() {
        return isolationStrategy.get();
    }

    /**
     * Sets the tenant database name.
     */
    public static void setTenantDatabase(String database) {
        tenantDatabase.set(database);
    }

    /**
     * Gets the tenant database name.
     */
    public static String getTenantDatabase() {
        return tenantDatabase.get();
    }

    /**
     * Sets the tenant schema name.
     */
    public static void setTenantSchema(String schema) {
        tenantSchema.set(schema);
    }

    /**
     * Gets the tenant schema name.
     */
    public static String getTenantSchema() {
        return tenantSchema.get();
    }

    /**
     * Clears all tenant context.
     */
    public static void clear() {
        currentTenant.remove();
        currentSchema.remove();
        isolationStrategy.remove();
        tenantDatabase.remove();
        tenantSchema.remove();
    }

    /**
     * Checks if tenant context is set.
     */
    public static boolean hasContext() {
        return currentTenant.get() != null;
    }

    /**
     * Creates a context snapshot for async operations.
     */
    public static TenantContextSnapshot createSnapshot() {
        return new TenantContextSnapshot(
                getCurrentTenant(),
                getCurrentSchema(),
                getIsolationStrategy(),
                getTenantDatabase(),
                getTenantSchema()
        );
    }

    /**
     * Restores context from a snapshot.
     */
    public static void restoreSnapshot(TenantContextSnapshot snapshot) {
        if (snapshot != null) {
            setCurrentTenant(snapshot.tenantId());
            setCurrentSchema(snapshot.currentSchema());
            setIsolationStrategy(snapshot.isolationStrategy());
            setTenantDatabase(snapshot.tenantDatabase());
            setTenantSchema(snapshot.tenantSchema());
        }
    }

    /**
     * Snapshot of tenant context for async operations.
     */
    public record TenantContextSnapshot(
            String tenantId,
            String currentSchema,
            TenantIsolationStrategy isolationStrategy,
            String tenantDatabase,
            String tenantSchema
    ) {}
}