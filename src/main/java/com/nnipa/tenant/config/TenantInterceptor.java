package com.nnipa.tenant.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

/**
 * Interceptor to extract and set tenant context from incoming requests
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantInterceptor implements HandlerInterceptor {

    private static final String TENANT_HEADER = "X-Tenant-Id";
    private static final String TENANT_CODE_HEADER = "X-Tenant-Code";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // Extract tenant information from request
        String tenantId = extractTenantId(request);
        String tenantCode = extractTenantCode(request);

        if (tenantId != null) {
            log.debug("Setting tenant context - ID: {}, Code: {}", tenantId, tenantCode);
            TenantContext.setCurrentTenant(tenantId);
            TenantContext.setCurrentTenantCode(tenantCode);
        }

        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
                           ModelAndView modelAndView) throws Exception {
        // Nothing to do here
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                Exception ex) throws Exception {
        // Clear tenant context after request completion
        TenantContext.clear();
    }

    /**
     * Extract tenant ID from request
     * Priority: Header > JWT Token > Subdomain > Path Parameter
     */
    private String extractTenantId(HttpServletRequest request) {
        // 1. Check header
        String tenantId = request.getHeader(TENANT_HEADER);
        if (tenantId != null && !tenantId.isEmpty()) {
            return tenantId;
        }

        // 2. Check JWT token (if present)
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            // Extract tenant ID from JWT token
            // This would need JWT parsing logic
            // For now, we'll skip this
        }

        // 3. Check subdomain
        String serverName = request.getServerName();
        if (serverName != null && !serverName.startsWith("www.")) {
            String[] parts = serverName.split("\\.");
            if (parts.length > 2) {
                // First part could be tenant code
                return parts[0];
            }
        }

        // 4. Check path parameter
        String path = request.getRequestURI();
        if (path != null && path.contains("/tenant/")) {
            // Extract tenant ID from path
            String[] pathParts = path.split("/");
            for (int i = 0; i < pathParts.length - 1; i++) {
                if ("tenant".equals(pathParts[i]) || "tenants".equals(pathParts[i])) {
                    return pathParts[i + 1];
                }
            }
        }

        return null;
    }

    /**
     * Extract tenant code from request
     */
    private String extractTenantCode(HttpServletRequest request) {
        return request.getHeader(TENANT_CODE_HEADER);
    }
}