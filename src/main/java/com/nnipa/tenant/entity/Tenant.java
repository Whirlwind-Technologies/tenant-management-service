package com.nnipa.tenant.entity;

import com.nnipa.tenant.enums.ComplianceFramework;
import com.nnipa.tenant.enums.OrganizationType;
import com.nnipa.tenant.enums.TenantIsolationStrategy;
import com.nnipa.tenant.enums.TenantStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Where;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Tenant entity representing an organization or individual user in the NNIPA platform.
 * Supports multiple organization types with flexible configuration.
 */
@Entity
@Table(name = "tenants", indexes = {
        @Index(name = "idx_tenant_code", columnList = "tenant_code", unique = true),
        @Index(name = "idx_tenant_status", columnList = "status"),
        @Index(name = "idx_tenant_org_type", columnList = "organization_type"),
        @Index(name = "idx_tenant_created", columnList = "created_at"),
        @Index(name = "idx_tenant_parent", columnList = "parent_tenant_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@ToString(exclude = {"subscription", "settings", "featureFlags", "childTenants"})
@EqualsAndHashCode(callSuper = true, exclude = {"subscription", "settings", "featureFlags", "childTenants"})
@Where(clause = "is_deleted = false")
public class Tenant extends BaseEntity {

    @Column(name = "tenant_code", nullable = false, unique = true, length = 50)
    private String tenantCode;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "display_name", length = 255)
    private String displayName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "organization_type", nullable = false, length = 50)
    private OrganizationType organizationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private TenantStatus status;

    // Organization Details
    @Column(name = "organization_email", length = 255)
    private String organizationEmail;

    @Column(name = "organization_phone", length = 50)
    private String organizationPhone;

    @Column(name = "organization_website", length = 500)
    private String organizationWebsite;

    // Add in metadata section:
    @Column(name = "marked_for_deletion_at")
    private Instant markedForDeletionAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "verified_by", length = 255)
    private String verifiedBy;

    // Add metadata field for flexible storage:
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata = new HashMap<>();


    @Column(name = "tax_id", length = 50)
    private String taxId;

    @Column(name = "business_license", length = 100)
    private String businessLicense;

    // Address Information
    @Column(name = "address_line1", length = 255)
    private String addressLine1;

    @Column(name = "address_line2", length = 255)
    private String addressLine2;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "state_province", length = 100)
    private String stateProvince;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(name = "country", length = 2)
    private String country; // ISO 3166-1 alpha-2

    // Compliance and Security
    @ElementCollection(targetClass = ComplianceFramework.class)
    @CollectionTable(
            name = "tenant_compliance_frameworks",
            joinColumns = @JoinColumn(name = "tenant_id")
    )
    @Column(name = "framework")
    @Enumerated(EnumType.STRING)
    private Set<ComplianceFramework> complianceFrameworks = new HashSet<>();

    @Column(name = "data_residency_region", length = 50)
    private String dataResidencyRegion;

    @Column(name = "security_level", length = 20)
    private String securityLevel; // STANDARD, ENHANCED, MAXIMUM

    // Data Isolation Configuration
    @Enumerated(EnumType.STRING)
    @Column(name = "isolation_strategy", length = 50)
    private TenantIsolationStrategy isolationStrategy;

    @Column(name = "database_name", length = 100)
    private String databaseName;

    @Column(name = "schema_name", length = 100)
    private String schemaName;

    @Column(name = "database_server", length = 255)
    private String databaseServer;

    @Column(name = "database_port")
    private Integer databasePort;

    @Column(name = "connection_pool_size")
    private Integer connectionPoolSize;

    // Verification and Validation
    @Column(name = "is_verified", nullable = false)
    private Boolean isVerified = false;

    @Column(name = "verification_document", length = 500)
    private String verificationDocument;

    // Multi-tenant Hierarchy (for departments/subsidiaries)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_tenant_id")
    private Tenant parentTenant;

    @OneToMany(mappedBy = "parentTenant", cascade = CascadeType.ALL)
    private Set<Tenant> childTenants = new HashSet<>();

    // Subscription and Billing
    @OneToOne(mappedBy = "tenant", cascade = CascadeType.ALL)
    private Subscription subscription;

    // Settings and Configuration
    @OneToOne(mappedBy = "tenant", cascade = CascadeType.ALL)
    private TenantSettings settings;

    // Feature Flags
    @OneToMany(mappedBy = "tenant", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<FeatureFlag> featureFlags = new HashSet<>();

    // Usage and Limits
    @Column(name = "max_users")
    private Integer maxUsers;

    @Column(name = "max_projects")
    private Integer maxProjects;

    @Column(name = "storage_quota_gb")
    private Integer storageQuotaGb;

    @Column(name = "api_rate_limit")
    private Integer apiRateLimit;

    // Activation and Expiration
    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "trial_ends_at")
    private Instant trialEndsAt;

    @Column(name = "suspended_at")
    private Instant suspendedAt;

    @Column(name = "suspension_reason", columnDefinition = "TEXT")
    private String suspensionReason;

    // Metadata
    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "primary_color", length = 7)
    private String primaryColor;

    @Column(name = "secondary_color", length = 7)
    private String secondaryColor;

    @Column(name = "timezone", length = 50)
    private String timezone = "UTC";

    @Column(name = "locale", length = 10)
    private String locale = "en_US";

    @Column(name = "tags", columnDefinition = "TEXT")
    private String tags; // Comma-separated tags for categorization

    // Helper Methods

    /**
     * Checks if the tenant is currently active and can be used.
     */
    public boolean isActive() {
        return status == TenantStatus.ACTIVE || status == TenantStatus.TRIAL;
    }

    /**
     * Checks if the tenant is in trial period.
     */
    public boolean isInTrial() {
        return status == TenantStatus.TRIAL &&
                trialEndsAt != null &&
                trialEndsAt.isAfter(Instant.now());
    }

    /**
     * Checks if the tenant requires high compliance.
     */
    public boolean requiresHighCompliance() {
        return organizationType.isRequiresHighCompliance() ||
                !complianceFrameworks.isEmpty();
    }

    /**
     * Adds a compliance framework to the tenant.
     */
    public void addComplianceFramework(ComplianceFramework framework) {
        if (complianceFrameworks == null) {
            complianceFrameworks = new HashSet<>();
        }
        complianceFrameworks.add(framework);
    }

    /**
     * Activates the tenant.
     */
    public void activate() {
        this.status = TenantStatus.ACTIVE;
        this.activatedAt = Instant.now();
    }

    /**
     * Suspends the tenant with a reason.
     */
    public void suspend(String reason) {
        this.status = TenantStatus.SUSPENDED;
        this.suspendedAt = Instant.now();
        this.suspensionReason = reason;
    }
}