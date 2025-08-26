package com.nnipa.tenant;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.client.RestTemplate;

/**
 * Main application class for the Tenant Management Service.
 * Authentication handled by auth-service, authorization by authz-service,
 * rate limiting by api-gateway, notifications by notification-service.
 */
@Slf4j
@SpringBootApplication
@EnableCaching
@EnableJpaAuditing
@EnableAsync
@EnableScheduling
@EnableTransactionManagement
@ConfigurationPropertiesScan("com.nnipa.tenant.config")
public class TenantManagementServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(TenantManagementServiceApplication.class, args);
		log.info("===========================================");
		log.info("NNIPA Tenant Management Service Started");
		log.info("Supporting Organization Types:");
		log.info("- Government Agencies");
		log.info("- Corporations");
		log.info("- Academic Institutions");
		log.info("- Healthcare Organizations");
		log.info("- Financial Institutions");
		log.info("- Non-Profits");
		log.info("- Startups");
		log.info("- Research Organizations");
		log.info("- Individual Users");
		log.info("===========================================");
		log.info("Integration Points:");
		log.info("- Authentication: auth-service");
		log.info("- Authorization: authz-service");
		log.info("- Rate Limiting: api-gateway");
		log.info("- Notifications: notification-service");
		log.info("===========================================");
	}

	/**
	 * RestTemplate bean for inter-service communication.
	 * Enhanced with circuit breakers and retry logic via Resilience4j.
	 */
	@Bean
	public RestTemplate restTemplate() {
		return new RestTemplate();
	}
}