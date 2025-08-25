package com.nnipa.tenant.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.Instant;

/**
 * Feature flag entity for controlling feature access per tenant.
 * Supports tier-based and time-based feature management.
 */
@Entity
@Table(name = "feature_flags", indexes = {
        @Index(name = "idx_feature_tenant", columnList = "tenant_id"),
        @Index(name = "idx_feature_code", columnList = "feature_code"),
        @Index(name = "idx_feature_enabled", columnList = "is_enabled"),
        @Index(name = "idx_feature_tenant_code", columnList = "tenant_id, feature_code", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "tenant")
@EqualsAndHashCode(callSuper = true, exclude = "tenant")
@Where(clause = "is_deleted = false")
public class FeatureFlag extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "feature_code", nullable = false, length = 100)
    private String featureCode;

    @Column(name = "feature_name", nullable = false, length = 255)
    private String featureName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "category", length = 50)
    private String category; // ANALYTICS, SECURITY, INTEGRATION, UI, DATA

    @Column(name = "is_enabled", nullable = false)
    private Boolean isEnabled = false;

    @Column(name = "is_beta", nullable = false)
    private Boolean isBeta = false;

    @Column(name = "is_experimental", nullable = false)
    private Boolean isExperimental = false;

    // Tier Requirements
    @Column(name = "required_plan", length = 30)
    private String requiredPlan; // Minimum subscription plan required

    @Column(name = "required_organization_type", length = 50)
    private String requiredOrganizationType;

    // Time-based Access
    @Column(name = "enabled_from")
    private Instant enabledFrom;

    @Column(name = "enabled_until")
    private Instant enabledUntil;

    @Column(name = "trial_enabled", nullable = false)
    private Boolean trialEnabled = false;

    @Column(name = "trial_days")
    private Integer trialDays;

    // Usage Limits
    @Column(name = "usage_limit")
    private Integer usageLimit;

    @Column(name = "current_usage")
    private Integer currentUsage = 0;

    @Column(name = "reset_frequency", length = 20)
    private String resetFrequency; // DAILY, WEEKLY, MONTHLY

    @Column(name = "last_reset_at")
    private Instant lastResetAt;

    // Configuration
    @Column(name = "config_json", columnDefinition = "TEXT")
    private String configJson; // Feature-specific configuration

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson; // Additional metadata

    // Access Control
    @Column(name = "requires_approval", nullable = false)
    private Boolean requiresApproval = false;

    @Column(name = "approved_by", length = 255)
    private String approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "approval_notes", columnDefinition = "TEXT")
    private String approvalNotes;

    // Dependencies
    @Column(name = "depends_on", length = 500)
    private String dependsOn; // Comma-separated list of feature codes

    @Column(name = "conflicts_with", length = 500)
    private String conflictsWith; // Comma-separated list of feature codes

    // Rollout Strategy
    @Column(name = "rollout_percentage")
    private Integer rolloutPercentage = 100;

    @Column(name = "rollout_group", length = 50)
    private String rolloutGroup; // A/B testing group

    // Tracking
    @Column(name = "first_enabled_at")
    private Instant firstEnabledAt;

    @Column(name = "last_enabled_at")
    private Instant lastEnabledAt;

    @Column(name = "total_enabled_days")
    private Integer totalEnabledDays = 0;

    @Column(name = "toggle_count")
    private Integer toggleCount = 0;

    // Helper Methods

    /**
     * Checks if the feature is currently accessible.
     */
    public boolean isAccessible() {
        if (!isEnabled) return false;

        Instant now = Instant.now();

        // Check time window
        if (enabledFrom != null && enabledFrom.isAfter(now)) return false;
        if (enabledUntil != null && enabledUntil.isBefore(now)) return false;

        // Check usage limit
        if (usageLimit != null && currentUsage >= usageLimit) return false;

        // Check approval if required
        if (requiresApproval && approvedAt == null) return false;

        return true;
    }

    /**
     * Increments the usage counter.
     */
    public void incrementUsage() {
        if (currentUsage == null) {
            currentUsage = 0;
        }
        currentUsage++;
    }

    /**
     * Resets the usage counter.
     */
    public void resetUsage() {
        currentUsage = 0;
        lastResetAt = Instant.now();
    }

    /**
     * Enables the feature.
     */
    public void enable() {
        if (!isEnabled) {
            isEnabled = true;
            lastEnabledAt = Instant.now();
            if (firstEnabledAt == null) {
                firstEnabledAt = Instant.now();
            }
            toggleCount = (toggleCount == null ? 0 : toggleCount) + 1;
        }
    }

    /**
     * Disables the feature.
     */
    public void disable() {
        isEnabled = false;
        toggleCount = (toggleCount == null ? 0 : toggleCount) + 1;
    }
}