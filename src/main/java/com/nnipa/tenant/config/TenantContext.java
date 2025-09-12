package com.nnipa.tenant.config;

/**
 * Thread-local storage for current tenant context
 */
public class TenantContext {

    private static final ThreadLocal<String> currentTenant = new ThreadLocal<>();
    private static final ThreadLocal<String> currentTenantCode = new ThreadLocal<>();

    /**
     * Set current tenant ID
     */
    public static void setCurrentTenant(String tenantId) {
        currentTenant.set(tenantId);
    }

    /**
     * Get current tenant ID
     */
    public static String getCurrentTenant() {
        return currentTenant.get();
    }

    /**
     * Set current tenant code
     */
    public static void setCurrentTenantCode(String tenantCode) {
        currentTenantCode.set(tenantCode);
    }

    /**
     * Get current tenant code
     */
    public static String getCurrentTenantCode() {
        return currentTenantCode.get();
    }

    /**
     * Clear tenant context
     */
    public static void clear() {
        currentTenant.remove();
        currentTenantCode.remove();
    }

    /**
     * Check if tenant context is set
     */
    public static boolean hasContext() {
        return currentTenant.get() != null;
    }
}