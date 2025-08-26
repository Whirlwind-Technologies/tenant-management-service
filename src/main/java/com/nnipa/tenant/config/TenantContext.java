package com.nnipa.tenant.config;

import com.nnipa.tenant.enums.OrganizationType;
import com.nnipa.tenant.enums.SubscriptionPlan;
import com.nnipa.tenant.enums.TenantIsolationStrategy;
import lombok.extern.slf4j.Slf4j;

/**
 * Thread-local storage for current tenant context.
 */
@Slf4j
public class TenantContext {

    private static final ThreadLocal<String> currentTenant = new ThreadLocal<>();
    private static final ThreadLocal<String> currentSchema = new ThreadLocal<>();
    private static final ThreadLocal<TenantIsolationStrategy> isolationStrategy = new ThreadLocal<>();
    private static final ThreadLocal<String> tenantDatabase = new ThreadLocal<>();
    private static final ThreadLocal<String> tenantSchema = new ThreadLocal<>();
    private static final ThreadLocal<OrganizationType> organizationType = new ThreadLocal<>();
    private static final ThreadLocal<SubscriptionPlan> subscriptionPlan = new ThreadLocal<>();

    public static void setCurrentTenant(String tenantId) {
        log.debug("Setting current tenant: {}", tenantId);
        currentTenant.set(tenantId);
    }

    public static String getCurrentTenant() {
        return currentTenant.get();
    }

    public static void setCurrentSchema(String schema) {
        log.debug("Setting current schema: {}", schema);
        currentSchema.set(schema);
    }

    public static String getCurrentSchema() {
        return currentSchema.get();
    }

    public static void setIsolationStrategy(TenantIsolationStrategy strategy) {
        log.debug("Setting isolation strategy: {}", strategy);
        isolationStrategy.set(strategy);
    }

    public static TenantIsolationStrategy getIsolationStrategy() {
        return isolationStrategy.get();
    }

    public static void setTenantDatabase(String database) {
        tenantDatabase.set(database);
    }

    public static String getTenantDatabase() {
        return tenantDatabase.get();
    }

    public static void setTenantSchema(String schema) {
        tenantSchema.set(schema);
    }

    public static String getTenantSchema() {
        return tenantSchema.get();
    }

    public static void setOrganizationType(OrganizationType type) {
        organizationType.set(type);
    }

    public static OrganizationType getOrganizationType() {
        return organizationType.get();
    }

    public static void setSubscriptionPlan(SubscriptionPlan plan) {
        subscriptionPlan.set(plan);
    }

    public static SubscriptionPlan getSubscriptionPlan() {
        return subscriptionPlan.get();
    }

    public static void clear() {
        currentTenant.remove();
        currentSchema.remove();
        isolationStrategy.remove();
        tenantDatabase.remove();
        tenantSchema.remove();
        organizationType.remove();
        subscriptionPlan.remove();
    }

    public static boolean hasContext() {
        return currentTenant.get() != null;
    }

    public static TenantContextSnapshot createSnapshot() {
        return new TenantContextSnapshot(
                getCurrentTenant(),
                getCurrentSchema(),
                getIsolationStrategy(),
                getTenantDatabase(),
                getTenantSchema(),
                getOrganizationType(),
                getSubscriptionPlan()
        );
    }

    public static void restoreSnapshot(TenantContextSnapshot snapshot) {
        if (snapshot != null) {
            setCurrentTenant(snapshot.tenantId());
            setCurrentSchema(snapshot.currentSchema());
            setIsolationStrategy(snapshot.isolationStrategy());
            setTenantDatabase(snapshot.tenantDatabase());
            setTenantSchema(snapshot.tenantSchema());
            setOrganizationType(snapshot.organizationType());
            setSubscriptionPlan(snapshot.subscriptionPlan());
        }
    }

    public record TenantContextSnapshot(
            String tenantId,
            String currentSchema,
            TenantIsolationStrategy isolationStrategy,
            String tenantDatabase,
            String tenantSchema,
            OrganizationType organizationType,
            SubscriptionPlan subscriptionPlan
    ) {}
}