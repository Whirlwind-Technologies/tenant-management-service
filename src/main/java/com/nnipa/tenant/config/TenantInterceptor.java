package com.nnipa.tenant.config;

import com.nnipa.tenant.entity.Tenant;
import com.nnipa.tenant.enums.TenantIsolationStrategy;
import com.nnipa.tenant.service.SchemaManagementService;
import com.nnipa.tenant.service.TenantService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

/**
 * Interceptor to extract and set tenant context for multi-tenant operations.
 * Authentication/authorization handled by separate services.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantInterceptor implements HandlerInterceptor {

    private static final String TENANT_HEADER = "X-Tenant-ID";
    private static final String TENANT_CODE_HEADER = "X-Tenant-Code";

    private final TenantService tenantService;
    private final SchemaManagementService schemaService;

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) throws Exception {

        String requestPath = request.getRequestURI();

        // Skip tenant context for public endpoints
        if (isPublicEndpoint(requestPath)) {
            return true;
        }

        String tenantIdentifier = extractTenantId(request);

        if (tenantIdentifier == null) {
            log.debug("No tenant identifier found for path: {}", requestPath);
            return true;
        }

        try {
            Tenant tenant = tenantService.findByCodeOrId(tenantIdentifier);

            if (tenant == null) {
                log.warn("Tenant not found: {}", tenantIdentifier);
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "Tenant not found");
                return false;
            }

            setupTenantContext(tenant);
            configureTenantIsolation(tenant);

            log.debug("Tenant context established for: {} ({})",
                    tenant.getName(), tenant.getIsolationStrategy());

            return true;

        } catch (Exception e) {
            log.error("Error setting up tenant context", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Error establishing tenant context");
            return false;
        }
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request,
                                @NonNull HttpServletResponse response,
                                @NonNull Object handler,
                                Exception ex) throws Exception {
        TenantContext.clear();
        schemaService.clearSchema();
        log.debug("Tenant context cleared");
    }

    private String extractTenantId(HttpServletRequest request) {
        // Check header
        String tenantId = request.getHeader(TENANT_HEADER);
        if (tenantId != null) return tenantId;

        // Check tenant code header
        String tenantCode = request.getHeader(TENANT_CODE_HEADER);
        if (tenantCode != null) return tenantCode;

        // Check subdomain
        String serverName = request.getServerName();
        if (serverName != null && !serverName.startsWith("www.")) {
            String[] parts = serverName.split("\\.");
            if (parts.length > 2) {
                return parts[0];
            }
        }

        // Check path variable
        String path = request.getRequestURI();
        if (path.contains("/tenants/")) {
            String[] pathParts = path.split("/");
            for (int i = 0; i < pathParts.length - 1; i++) {
                if ("tenants".equals(pathParts[i])) {
                    return pathParts[i + 1];
                }
            }
        }

        return null;
    }

    private void setupTenantContext(Tenant tenant) {
        TenantContext.setCurrentTenant(tenant.getId().toString());
        TenantContext.setIsolationStrategy(tenant.getIsolationStrategy());
        TenantContext.setTenantDatabase(tenant.getDatabaseName());
        TenantContext.setTenantSchema(tenant.getSchemaName());

        // Add organization type and subscription plan for business logic
        if (tenant.getOrganizationType() != null) {
            TenantContext.setOrganizationType(tenant.getOrganizationType());
        }
        if (tenant.getSubscription() != null) {
            TenantContext.setSubscriptionPlan(tenant.getSubscription().getPlan());
        }
    }

    private void configureTenantIsolation(Tenant tenant) {
        TenantIsolationStrategy strategy = tenant.getIsolationStrategy();

        switch (strategy) {
            case DATABASE_PER_TENANT -> {
                // Database routing handled by MultiTenantDataSourceConfig
            }
            case SCHEMA_PER_TENANT, HYBRID_POOL -> {
                String schemaName = tenant.getSchemaName() != null ?
                        tenant.getSchemaName() : "tenant_" + tenant.getId();
                schemaService.setSchema(schemaName);
            }
            case SHARED_SCHEMA_ROW_LEVEL -> {
                schemaService.setTenantIdForRLS(tenant.getId().toString());
            }
            default -> {
                // SHARED_SCHEMA_BASIC - no special configuration needed
            }
        }
    }

    private boolean isPublicEndpoint(String path) {
        return path.contains("/public/") ||
                path.contains("/health") ||
                path.contains("/actuator/") ||
                path.contains("/swagger") ||
                path.contains("/api-docs") ||
                path.contains("/register");
    }
}