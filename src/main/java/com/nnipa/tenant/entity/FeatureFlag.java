package com.nnipa.tenant.entity;

import com.nnipa.tenant.enums.FeatureCategory;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Where;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Feature Flag entity for tenant-specific feature management
 */
@Entity
@Table(name = "feature_flags", indexes = {
        @Index(name = "idx_feature_tenant", columnList = "tenant_id"),
        @Index(name = "idx_feature_code", columnList = "feature_code"),
        @Index(name = "idx_feature_enabled", columnList = "is_enabled"),
        @Index(name = "idx_feature_category", columnList = "category"),
        @Index(name = "uk_feature_tenant_code", columnList = "tenant_id,feature_code", unique = true)
})
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
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
    @Enumerated(EnumType.STRING)
    private FeatureCategory category;

    // Status
    @Column(name = "is_enabled", nullable = false)
    private Boolean isEnabled = false;

    @Column(name = "is_beta", nullable = false)
    private Boolean isBeta = false;

    @Column(name = "is_experimental", nullable = false)
    private Boolean isExperimental = false;

    // Requirements
    @Column(name = "required_plan", length = 30)
    private String requiredPlan;

    @Column(name = "required_organization_type", length = 50)
    private String requiredOrganizationType;

    // Time-based access
    @Column(name = "enabled_from")
    private LocalDateTime enabledFrom;

    @Column(name = "enabled_until")
    private LocalDateTime enabledUntil;

    @Column(name = "trial_enabled", nullable = false)
    private Boolean trialEnabled = false;

    @Column(name = "trial_days")
    private Integer trialDays;

    // Usage limits
    @Column(name = "usage_limit")
    private Integer usageLimit;

    @Column(name = "current_usage")
    private Integer currentUsage = 0;

    @Column(name = "reset_frequency", length = 20)
    private String resetFrequency;

    @Column(name = "last_reset_at")
    private LocalDateTime lastResetAt;

    // Configuration
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config_json", columnDefinition = "jsonb")
    private Map<String, Object> configJson = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", columnDefinition = "jsonb")
    private Map<String, Object> metadataJson = new HashMap<>();

    // Approval
    @Column(name = "requires_approval", nullable = false)
    private Boolean requiresApproval = false;

    @Column(name = "approved_by", length = 255)
    private String approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "approval_notes", columnDefinition = "TEXT")
    private String approvalNotes;

    // Dependencies
    @Column(name = "depends_on", length = 500)
    private String dependsOn;

    @Column(name = "conflicts_with", length = 500)
    private String conflictsWith;

    // Rollout
    @Column(name = "rollout_percentage")
    private Integer rolloutPercentage = 100;

    @Column(name = "rollout_group", length = 50)
    private String rolloutGroup;

    // Tracking
    @Column(name = "first_enabled_at")
    private LocalDateTime firstEnabledAt;

    @Column(name = "last_enabled_at")
    private LocalDateTime lastEnabledAt;

    @Column(name = "total_enabled_days")
    private Integer totalEnabledDays = 0;

    @Column(name = "toggle_count")
    private Integer toggleCount = 0;

    @PrePersist
    @Override
    protected void onCreate() {
        super.onCreate();
        if (isEnabled == null) isEnabled = false;
        if (isBeta == null) isBeta = false;
        if (isExperimental == null) isExperimental = false;
        if (trialEnabled == null) trialEnabled = false;
        if (requiresApproval == null) requiresApproval = false;
        if (currentUsage == null) currentUsage = 0;
        if (rolloutPercentage == null) rolloutPercentage = 100;
        if (totalEnabledDays == null) totalEnabledDays = 0;
        if (toggleCount == null) toggleCount = 0;
    }

    // Business methods
    public void enable() {
        if (!this.isEnabled) {
            this.isEnabled = true;
            this.lastEnabledAt = LocalDateTime.now();
            if (this.firstEnabledAt == null) {
                this.firstEnabledAt = LocalDateTime.now();
            }
            this.toggleCount++;
        }
    }

    public void disable() {
        if (this.isEnabled) {
            this.isEnabled = false;
            this.toggleCount++;
        }
    }

    public boolean isAvailable() {
        LocalDateTime now = LocalDateTime.now();

        // Check time-based availability
        if (enabledFrom != null && enabledFrom.isAfter(now)) {
            return false;
        }
        if (enabledUntil != null && enabledUntil.isBefore(now)) {
            return false;
        }

        // Check usage limits
        if (usageLimit != null && currentUsage >= usageLimit) {
            return false;
        }

        return isEnabled;
    }

    public void incrementUsage() {
        if (currentUsage == null) {
            currentUsage = 0;
        }
        currentUsage++;
    }

    public void resetUsage() {
        currentUsage = 0;
        lastResetAt = LocalDateTime.now();
    }
}