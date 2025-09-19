package com.nnipa.tenant.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nnipa.tenant.enums.*;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Request DTO for creating a new tenant
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateTenantRequest {

    @NotBlank(message = "Tenant code is required")
    @Size(min = 3, max = 50, message = "Tenant code must be between 3 and 50 characters")
    @Pattern(regexp = "^[A-Z0-9-]+$", message = "Tenant code must contain only uppercase letters, numbers, and hyphens")
    private String tenantCode;

    @NotBlank(message = "Name is required")
    @Size(max = 255)
    private String name;

    @Size(max = 255)
    private String displayName;

    @NotNull(message = "Organization type is required")
    private OrganizationType organizationType;

    @Email(message = "Invalid email format")
    @Size(max = 255)
    private String organizationEmail;

    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Invalid phone number format")
    private String organizationPhone;

    @Size(max = 500)
    private String organizationWebsite;

    // Address
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String stateProvince;
    private String postalCode;

    @Size(min = 2, max = 2, message = "Country code must be 2 characters")
    private String country;

    // Subscription
    @NotNull(message = "Subscription plan is required")
    private SubscriptionPlan subscriptionPlan;

    private BillingCycle billingCycle;
    private BigDecimal monthlyPrice;
    private String currency;

    // Settings
    private Integer maxUsers;
    private Integer maxProjects;
    private Integer storageQuotaGb;
    private Integer apiRateLimit;

    // Optional parent tenant for hierarchical structure
    private String parentTenantCode;

    // Auto-activation flag
    @Builder.Default
    private Boolean autoActivate = false;

    // Trial configuration
    private LocalDateTime trialEndsAt;

    // Initial settings
    private Map<String, String> initialSettings;

    // Initial feature flags to enable
    private Map<String, Boolean> initialFeatureFlags;

    // Isolation strategy for multi-tenancy
    private IsolationStrategy isolationStrategy;

    // Billing email (can be different from organization email)
    @Email(message = "Invalid billing email format")
    @Size(max = 255)
    private String billingEmail;

    // Metadata for additional tenant information
    private Map<String, String> metadata;
}