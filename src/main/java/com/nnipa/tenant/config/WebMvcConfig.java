package com.nnipa.tenant.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC configuration to register interceptors
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final TenantInterceptor tenantInterceptor;
    private final RequestIdInterceptor requestIdInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Register RequestIdInterceptor first
        registry.addInterceptor(requestIdInterceptor)
                .addPathPatterns("/api/**")
                .order(1);

        // Register TenantInterceptor second
        registry.addInterceptor(tenantInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/v1/public/**",
                        "/api/v1/auth/**",
                        "/api/v1/register/**",
                        "/api/v1/health/**",
                        "/swagger-ui/**",
                        "/api-docs/**"
                )
                .order(2);
    }
}