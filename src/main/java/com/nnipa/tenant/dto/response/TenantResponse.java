package com.nnipa.tenant.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.nnipa.tenant.enums.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Response DTO for tenant information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TenantResponse {

    private UUID id;
    private String tenantCode;
    private String name;
    private String displayName;
    private OrganizationType organizationType;
    private TenantStatus status;

    // Contact
    private String organizationEmail;
    private String organizationPhone;
    private String organizationWebsite;

    // Address
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String stateProvince;
    private String postalCode;
    private String country;

    // Status
    private LocalDateTime activatedAt;
    private LocalDateTime suspendedAt;
    private String suspensionReason;
    private LocalDateTime trialEndsAt;

    // Verification
    private Boolean isVerified;
    private LocalDateTime verifiedAt;

    // Limits
    private Integer maxUsers;
    private Integer maxProjects;
    private Integer storageQuotaGb;
    private Integer apiRateLimit;

    // Hierarchy
    private UUID parentTenantId;
    private String parentTenantCode;
    private List<TenantSummaryResponse> childTenants;

    // Subscription summary
    private SubscriptionSummaryResponse subscription;

    // Feature flags summary
    private Integer enabledFeaturesCount;
    private List<String> enabledFeatureCodes;

    // Audit
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
}

