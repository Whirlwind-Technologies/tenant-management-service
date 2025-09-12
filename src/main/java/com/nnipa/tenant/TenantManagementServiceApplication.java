package com.nnipa.tenant;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Tenant Management Service Application
 *
 * This service manages the multi-tenant architecture of the NNIPA platform, providing:
 * - Multi-tenant configuration and provisioning
 * - Tenant metadata and settings management
 * - Billing and subscription management
 * - Feature flags per tenant
 *
 * Key Features:
 * - RESTful APIs for tenant CRUD operations
 * - Event-driven architecture with Kafka for inter-service communication
 * - Protobuf message serialization for efficient data transfer
 * - Redis caching for improved performance
 * - PostgreSQL for persistent data storage
 * - Comprehensive audit logging
 * - Flexible subscription and billing models
 * - Granular feature flag management
 *
 * Integration Points:
 * - Auth Service: Receives user authentication events
 * - User Management Service: Coordinates user-tenant associations
 * - Event Streaming Service: Publishes tenant lifecycle events
 * - Notification Service: Triggers notifications for tenant events
 * - Data Storage Service: Manages tenant-specific data isolation
 */
@Slf4j
@SpringBootApplication(exclude = {
		RedisRepositoriesAutoConfiguration.class
})
@EnableKafka
@EnableCaching
@EnableAsync
@EnableScheduling
public class TenantManagementServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(TenantManagementServiceApplication.class, args);

		log.info("========================================");
		log.info("NNIPA Tenant Management Service Started");
		log.info("========================================");
		log.info("Service Capabilities:");
		log.info("- Multi-tenant provisioning and management");
		log.info("- Subscription and billing management");
		log.info("- Feature flag configuration");
		log.info("- Tenant settings and metadata");
		log.info("- Event-driven integration via Kafka");
		log.info("========================================");
	}
}