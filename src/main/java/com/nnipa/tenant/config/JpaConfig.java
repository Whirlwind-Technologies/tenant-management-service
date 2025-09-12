package com.nnipa.tenant.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.util.Optional;

/**
 * JPA and transaction configuration
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.nnipa.tenant.repository")
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
@EnableTransactionManagement
public class JpaConfig {

    /**
     * Auditor provider for JPA auditing
     */
    @Bean
    public AuditorAware<String> auditorProvider() {
        return new TenantAuditorAware();
    }

    /**
     * Custom auditor aware implementation
     */
    public static class TenantAuditorAware implements AuditorAware<String> {

        @Override
        public Optional<String> getCurrentAuditor() {
            // Get current user from security context or tenant context
            // For now, return system user
            String currentUser = TenantContext.getCurrentTenant();
            if (currentUser != null) {
                return Optional.of(currentUser);
            }
            return Optional.of("system");
        }
    }
}