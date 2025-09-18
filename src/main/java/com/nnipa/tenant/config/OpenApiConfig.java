package com.nnipa.tenant.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;

/**
 * OpenAPI/Swagger configuration for the Tenant Management Service.
 */
@Configuration
public class OpenApiConfig {

    @Value("${server.servlet.context-path:}")
    private String contextPath;

    @Value("${server.port:4001}")
    private String serverPort;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(apiInfo())
                .servers(servers())
                .components(components())
                .security(Arrays.asList(
                        new SecurityRequirement().addList("bearerAuth"),
                        new SecurityRequirement().addList("apiKey")
                ))
                .tags(apiTags());
    }

    private Info apiInfo() {
        return new Info()
                .title("Tenant Management Service API")
                .description("NNIPA Platform Tenant Management Service - Manages multi-tenant configuration, " +
                        "provisioning, metadata, settings, billing, subscriptions, and feature flags. " +
                        "\n\n" +
                        "## Key Features\n" +
                        "- **Multi-tenant Configuration**: Provisioning and isolation strategies\n" +
                        "- **Subscription Management**: Plans, billing cycles, and payment processing\n" +
                        "- **Feature Flags**: Granular feature control with usage limits and A/B testing\n" +
                        "- **Billing & Usage**: Metered billing, usage tracking, and invoice management\n" +
                        "- **Settings Management**: Tenant-specific configurations and preferences\n" +
                        "\n\n" +
                        "## Authentication\n" +
                        "This service requires JWT Bearer token authentication obtained from the Auth Service. " +
                        "Include the token in the Authorization header: `Bearer <token>`")
                .version("1.0.0")
                .contact(new Contact()
                        .name("NNIPA Platform Team")
                        .email("tenant-support@nnipa.cloud")
                        .url("https://nnipa.cloud"))
                .license(new License()
                        .name("Proprietary")
                        .url("https://nnipa.cloud/license"));
    }

    private List<Server> servers() {
        String basePath = StringUtils.hasText(contextPath) ? contextPath : "";

        Server gatewayServer = new Server()
                .url("http://localhost:4000/api/v1/tenant-management")
                .description("Via API Gateway");

        Server localServer = new Server()
                .url("http://localhost:" + serverPort + basePath)
                .description("Direct Local Access");

        Server dockerServer = new Server()
                .url("http://tenant-service:4000" + basePath)
                .description("Docker Network Access");

        Server devServer = new Server()
                .url("https://dev-api.nnipa.cloud/api/v1/tenant-management")
                .description("Development Server (via Gateway)");

        Server stagingServer = new Server()
                .url("https://staging-api.nnipa.cloud/api/v1/tenant-management")
                .description("Staging Server (via Gateway)");

        Server prodServer = new Server()
                .url("https://api.nnipa.cloud/api/v1/tenant-management")
                .description("Production Server (via Gateway)");

        return Arrays.asList(gatewayServer, localServer, dockerServer, devServer, stagingServer, prodServer);
    }

    private Components components() {
        return new Components()
                .addSecuritySchemes("bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT Bearer Token Authentication. " +
                                        "Obtain the token from the Auth Service at `/api/v1/auth/login`. " +
                                        "The token contains user identity, tenant context, and roles."))
                .addSecuritySchemes("apiKey",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-API-Key")
                                .description("API Key Authentication for service-to-service communication. " +
                                        "Contact the platform administrator to obtain an API key."))
                .addSecuritySchemes("tenantContext",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-Tenant-Id")
                                .description("Tenant Context Header. Required for multi-tenant operations. " +
                                        "Automatically extracted from JWT token or can be explicitly provided."));
    }

    private List<Tag> apiTags() {
        return Arrays.asList(
                new Tag()
                        .name("Tenant Management")
                        .description("Core tenant CRUD operations, activation, suspension, and lifecycle management"),

                new Tag()
                        .name("Subscription Management")
                        .description("Subscription plans, renewals, cancellations, and billing cycle management"),

                new Tag()
                        .name("Feature Flag Management")
                        .description("Feature flags configuration, enablement, usage tracking, and A/B testing"),

                new Tag()
                        .name("Billing Management")
                        .description("Usage tracking, billing processing, invoices, and payment methods"),

                new Tag()
                        .name("Tenant Settings")
                        .description("Tenant-specific configurations, preferences, security settings, and customizations"),

                new Tag()
                        .name("Health & Monitoring")
                        .description("Service health checks, metrics, and monitoring endpoints")
        );
    }
}