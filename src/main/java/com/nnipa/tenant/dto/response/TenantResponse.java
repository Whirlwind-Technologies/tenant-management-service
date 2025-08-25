package com.nnipa.tenant.dto.response;

import com.nnipa.tenant.enums.OrganizationType;
import com.nnipa.tenant.enums.TenantIsolationStrategy;
import com.nnipa.tenant.enums.TenantStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Tenant information response")
public class TenantResponse {

    @Schema(description = "Tenant ID")
    private UUID id;

    @Schema(description = "Unique tenant code")
    private String tenantCode;

    @Schema(description = "Organization name")
    private String name;

    @Schema(description = "Display name")
    private String displayName;

    @Schema(description = "Organization type")
    private OrganizationType organizationType;

    @Schema(description = "Tenant status")
    private TenantStatus status;

    @Schema(description = "Data isolation strategy")
    private TenantIsolationStrategy isolationStrategy;

    @Schema(description = "Organization email")
    private String organizationEmail;

    @Schema(description = "Organization phone")
    private String organizationPhone;

    @Schema(description = "Organization website")
    private String organizationWebsite;

    @Schema(description = "Country")
    private String country;

    @Schema(description = "Compliance frameworks")
    private Set<String> complianceFrameworks;

    @Schema(description = "Data residency region")
    private String dataResidencyRegion;

    @Schema(description = "Security level")
    private String securityLevel;

    @Schema(description = "Is verified")
    private Boolean isVerified;

    @Schema(description = "Maximum users")
    private Integer maxUsers;

    @Schema(description = "Maximum projects")
    private Integer maxProjects;

    @Schema(description = "Storage quota in GB")
    private Integer storageQuotaGb;

    @Schema(description = "API rate limit")
    private Integer apiRateLimit;

    @Schema(description = "Subscription plan")
    private String subscriptionPlan;

    @Schema(description = "Subscription status")
    private String subscriptionStatus;

    @Schema(description = "Created timestamp")
    private Instant createdAt;

    @Schema(description = "Activated timestamp")
    private Instant activatedAt;

    @Schema(description = "Trial ends timestamp")
    private Instant trialEndsAt;

    @Schema(description = "Expires timestamp")
    private Instant expiresAt;
}