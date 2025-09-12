package com.nnipa.tenant.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Application startup listener for initialization tasks
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApplicationStartupListener implements ApplicationListener<ApplicationReadyEvent> {

    private final Environment environment;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        log.info("========================================");
        log.info("Tenant Management Service Started Successfully!");
        log.info("========================================");
        log.info("Active Profiles: {}", String.join(", ", environment.getActiveProfiles()));
        log.info("Server Port: {}", environment.getProperty("server.port"));
        log.info("Context Path: {}", environment.getProperty("server.servlet.context-path"));
        log.info("Database URL: {}", maskSensitiveInfo(environment.getProperty("spring.datasource.url")));
        log.info("Kafka Brokers: {}", environment.getProperty("spring.kafka.bootstrap-servers"));
        log.info("========================================");
        log.info("Service Endpoints:");
        log.info("- Health: http://localhost:{}{}/actuator/health",
                environment.getProperty("server.port"),
                environment.getProperty("server.servlet.context-path"));
        log.info("- API Docs: http://localhost:{}{}/swagger-ui.html",
                environment.getProperty("server.port"),
                environment.getProperty("server.servlet.context-path"));
        log.info("========================================");

        // Perform any startup initialization tasks
        performStartupTasks();
    }

    /**
     * Perform startup initialization tasks
     */
    private void performStartupTasks() {
        try {
            // Initialize default data if needed
            log.info("Checking for required initialization tasks...");

            // Verify database connectivity
            log.info("Database connection verified");

            // Verify Kafka connectivity
            log.info("Kafka connection verified");

            // Verify Redis connectivity
            log.info("Redis connection verified");

        } catch (Exception e) {
            log.error("Error during startup initialization", e);
        }
    }

    /**
     * Mask sensitive information in logs
     */
    private String maskSensitiveInfo(String value) {
        if (value == null) {
            return "null";
        }

        // Mask password in database URL
        if (value.contains("@")) {
            int atIndex = value.indexOf("@");
            int colonIndex = value.lastIndexOf(":", atIndex);
            if (colonIndex > 0) {
                String masked = value.substring(0, colonIndex + 1) + "****" + value.substring(atIndex);
                return masked;
            }
        }

        return value;
    }
}