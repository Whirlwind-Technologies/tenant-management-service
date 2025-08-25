package com.nnipa.tenant.config;

import com.nnipa.tenant.entity.Tenant;
import com.nnipa.tenant.repository.TenantRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

/**
 * Interceptor to extract tenant information from HTTP requests
 * and set up the tenant context for multi-tenant data access.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantInterceptor implements HandlerInterceptor {

    private static final String TENANT_HEADER = "X-Tenant-ID";
    private static final String TENANT_CODE_HEADER = "X-Tenant-Code";
    private static final String SCHEMA_HEADER = "X-Schema";

    private final TenantRepository tenantRepository;
    private final SchemaService schemaService;

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) throws Exception {

        // Extract tenant identifier from request
        String tenantId = extractTenantId(request);

        if (tenantId == null) {
            // For public endpoints or initial setup
            log.debug("No tenant context found in request to {}", request.getRequestURI());
            return true;
        }

        try {
            // Load tenant configuration
            Tenant tenant = tenantRepository.findByIdOrTenantCode(tenantId, tenantId)
                    .orElseThrow(() -> new TenantNotFoundException("Tenant not found: " + tenantId));

            // Validate tenant is active
            if (!tenant.isActive()) {
                log.warn("Inactive tenant attempted access: {}", tenantId);
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Tenant is not active");
                return false;
            }

            // Set up tenant context
            setupTenantContext(tenant);

            // Set schema if using schema-per-tenant
            if (tenant.getIsolationStrategy() != null) {
                switch (tenant.getIsolationStrategy()) {
                    case SCHEMA_PER_TENANT, HYBRID_POOL -> {
                        String schemaName = tenant.getSchemaName() != null ?
                                tenant.getSchemaName() : "tenant_" + tenant.getId();
                        schemaService.setSchema(schemaName);
                    }
                    case SHARED_SCHEMA_ROW_LEVEL -> {
                        // Set tenant ID for row-level security
                        schemaService.setTenantIdForRLS(tenant.getId().toString());
                    }
                    default -> {
                        // No special schema handling needed
                    }
                }
            }

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
    public void postHandle(@NonNull HttpServletRequest request,
                           @NonNull HttpServletResponse response,
                           @NonNull Object handler,
                           ModelAndView modelAndView) throws Exception {
        // No post-processing needed
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request,
                                @NonNull HttpServletResponse response,
                                @NonNull Object handler,
                                Exception ex) throws Exception {
        // Clear tenant context after request completion
        TenantContext.clear();
        schemaService.clearSchema();
        log.debug("Tenant context cleared after request completion");
    }

    /**
     * Extracts tenant identifier from various sources in the request.
     */
    private String extractTenantId(HttpServletRequest request) {
        // 1. Check header
        String tenantId = request.getHeader(TENANT_HEADER);
        if (tenantId != null) {
            return tenantId;
        }

        // 2. Check tenant code header
        String tenantCode = request.getHeader(TENANT_CODE_HEADER);
        if (tenantCode != null) {
            return tenantCode;
        }

        // 3. Check subdomain (e.g., tenant1.app.com)
        String serverName = request.getServerName();
        if (serverName != null && !serverName.startsWith("www.")) {
            String[] parts = serverName.split("\\.");
            if (parts.length > 2) {
                return parts[0]; // First part is tenant identifier
            }
        }

        // 4. Check JWT token claims (if using JWT)
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            // Extract tenant from JWT (implementation depends on JWT library)
            // This is a placeholder - actual implementation would decode JWT
            return extractTenantFromJWT(authHeader.substring(7));
        }

        // 5. Check request parameter (for certain endpoints)
        String paramTenant = request.getParameter("tenant");
        if (paramTenant != null) {
            return paramTenant;
        }

        // 6. Check path variable (e.g., /api/v1/tenants/{tenantId}/...)
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

    /**
     * Sets up the tenant context with all necessary information.
     */
    private void setupTenantContext(Tenant tenant) {
        TenantContext.setCurrentTenant(tenant.getId().toString());
        TenantContext.setIsolationStrategy(tenant.getIsolationStrategy());
        TenantContext.setTenantDatabase(tenant.getDatabaseName());
        TenantContext.setTenantSchema(tenant.getSchemaName());
    }

    /**
     * Placeholder for JWT tenant extraction.
     */
    private String extractTenantFromJWT(String token) {
        // TODO: Implement JWT parsing to extract tenant claim
        return null;
    }

    /**
     * Custom exception for tenant not found.
     */
    public static class TenantNotFoundException extends RuntimeException {
        public TenantNotFoundException(String message) {
            super(message);
        }
    }
}