package com.nnipa.tenant.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC configuration to register the tenant interceptor.
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final TenantInterceptor tenantInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tenantInterceptor)
                .addPathPatterns("/api/**")  // Apply to all API endpoints
                .excludePathPatterns(
                        "/api/v1/public/**",      // Public endpoints
                        "/api/v1/auth/**",        // Authentication endpoints
                        "/api/v1/register/**",    // Registration endpoints
                        "/api/v1/health/**",      // Health check endpoints
                        "/swagger-ui/**",         // API documentation
                        "/api-docs/**"            // OpenAPI specs
                );
    }
}