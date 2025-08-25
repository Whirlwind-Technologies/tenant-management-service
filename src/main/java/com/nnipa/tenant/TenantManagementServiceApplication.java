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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.client.RestTemplate;

/**
 * Main application class for the Tenant Management Service.
 * This service provides multi-tenant configuration and provisioning for the NNIPA platform,
 * supporting diverse organization types including government agencies, corporations,
 * academic institutions, and individual users.
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
		log.info("- Individual Users");
		log.info("===========================================");
	}

	/**
	 * RestTemplate bean for inter-service communication.
	 * Will be enhanced with circuit breakers and retry logic.
	 */
	@Bean
	public RestTemplate restTemplate() {
		return new RestTemplate();
	}

	/**
	 * Password encoder for secure password storage.
	 * Using BCrypt for production-ready security.
	 */
	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}