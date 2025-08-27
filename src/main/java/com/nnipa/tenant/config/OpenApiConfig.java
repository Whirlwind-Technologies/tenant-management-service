package com.nnipa.tenant.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI/Swagger configuration for the Tenant Management Service.
 * Configures API documentation with proper context path and security.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(apiInfo());
    }

    private Info apiInfo() {
        return new Info()
                .title("Tenant Management Service API")
                .description("Multi-tenant management service for the Nnipa platform. " +
                        "Provides comprehensive tenant lifecycle management, subscription handling, " +
                        "feature flags, and usage tracking.")
                .version("1.0.0")
                .contact(new Contact()
                        .name("Nnipa Platform Team")
                        .email("support@nnipa.cloud")
                        .url("https://nnipa.cloud"))
                .license(new License()
                        .name("Proprietary")
                        .url("https://nnipa.cloud/license"));
    }


}