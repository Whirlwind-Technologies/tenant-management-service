package com.nnipa.tenant.entity;

import com.nnipa.tenant.enums.IsolationStrategy;
import com.nnipa.tenant.enums.OrganizationType;
import com.nnipa.tenant.enums.TenantStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Core Tenant entity representing an organization/customer
 */
@Entity
@Table(name = "tenants", indexes = {
        @Index(name = "idx_tenant_code", columnList = "tenant_code", unique = true),
        @Index(name = "idx_tenant_status", columnList = "status"),
        @Index(name = "idx_tenant_org_type", columnList = "organization_type"),
        @Index(name = "idx_tenant_parent", columnList = "parent_tenant_id"),
        @Index(name = "idx_tenant_created", columnList = "created_at"),
        @Index(name = "idx_tenant_deleted", columnList = "is_deleted")
})
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Where(clause = "is_deleted = false")
public class Tenant extends BaseEntity {

    @Column(name = "tenant_code", nullable = false, unique = true, length = 50)
    private String tenantCode;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "display_name", length = 255)
    private String displayName;

    @Column(name = "organization_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private OrganizationType organizationType;

    @Column(name = "status", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private TenantStatus status;

    @Column(name = "organization_email", length = 255)
    private String organizationEmail;

    @Column(name = "organization_phone", length = 50)
    private String organizationPhone;

    @Column(name = "organization_website", length = 500)
    private String organizationWebsite;

    @Column(name = "isolation_strategy", length = 50)
    @Enumerated(EnumType.STRING)
    private IsolationStrategy isolationStrategy;

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
    private String country;

    // Status and Lifecycle
    @Column(name = "activated_at")
    private LocalDateTime activatedAt;

    @Column(name = "suspended_at")
    private LocalDateTime suspendedAt;

    @Column(name = "suspension_reason", length = 500)
    private String suspensionReason;

    @Column(name = "trial_ends_at")
    private LocalDateTime trialEndsAt;

    // Verification and Validation
    @Column(name = "is_verified", nullable = false)
    private Boolean isVerified = false;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "verification_document", length = 500)
    private String verificationDocument;

    // Usage and Limits
    @Column(name = "max_users")
    private Integer maxUsers;

    @Column(name = "max_projects")
    private Integer maxProjects;

    @Column(name = "storage_quota_gb")
    private Integer storageQuotaGb;

    @Column(name = "api_rate_limit")
    private Integer apiRateLimit;

    @Column(name = "user_count")
    private Integer userCount = 0;

    /**
     * Metadata key-value pairs for extensible tenant properties
     */
    @ElementCollection
    @CollectionTable(
            name = "tenant_metadata",
            joinColumns = @JoinColumn(name = "tenant_id")
    )
    @MapKeyColumn(name = "metadata_key")
    @Column(name = "metadata_value", length = 1000)
    private Map<String, String> metadata = new HashMap<>();

    // Multi-tenant Hierarchy
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_tenant_id")
    private Tenant parentTenant;

    @OneToMany(mappedBy = "parentTenant", cascade = CascadeType.ALL)
    private Set<Tenant> childTenants = new HashSet<>();

    // Related Entities
    @OneToOne(mappedBy = "tenant", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Subscription subscription;

    @OneToOne(mappedBy = "tenant", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private TenantSettings settings;

    @OneToMany(mappedBy = "tenant", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<FeatureFlag> featureFlags = new HashSet<>();

    @Override
    protected void onCreate() {
        super.onCreate();
        if (status == null) {
            status = TenantStatus.PENDING;
        }
        if (isVerified == null) {
            isVerified = false;
        }
    }

    // Business methods
    public void activate() {
        this.status = TenantStatus.ACTIVE;
        this.activatedAt = LocalDateTime.now();
    }

    public void suspend(String reason) {
        this.status = TenantStatus.SUSPENDED;
        this.suspendedAt = LocalDateTime.now();
        this.suspensionReason = reason;
    }

    public void reactivate() {
        this.status = TenantStatus.ACTIVE;
        this.suspendedAt = null;
        this.suspensionReason = null;
    }

    public boolean isActive() {
        return TenantStatus.ACTIVE.equals(status);
    }

    public boolean isTrial() {
        return TenantStatus.TRIAL.equals(status) &&
                (trialEndsAt == null || trialEndsAt.isAfter(LocalDateTime.now()));
    }

    public void addMetadata(String key, String value) {
        if (metadata == null) {
            metadata = new HashMap<>();
        }
        metadata.put(key, value);
    }

    public String getMetadataValue(String key) {
        return metadata != null ? metadata.get(key) : null;
    }

    public void removeMetadata(String key) {
        if (metadata != null) {
            metadata.remove(key);
        }
    }
}