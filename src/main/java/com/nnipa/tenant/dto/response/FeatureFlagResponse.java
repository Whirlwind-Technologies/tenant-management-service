package com.nnipa.tenant.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.nnipa.tenant.enums.FeatureCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID; /**
 * Response DTO for feature flags
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FeatureFlagResponse {

    private UUID id;
    private UUID tenantId;
    private String featureCode;
    private String featureName;
    private String description;
    private FeatureCategory category;

    // Status
    private Boolean isEnabled;
    private Boolean isAvailable;
    private Boolean isBeta;
    private Boolean isExperimental;

    // Requirements
    private String requiredPlan;
    private String requiredOrganizationType;

    // Time-based
    private LocalDateTime enabledFrom;
    private LocalDateTime enabledUntil;
    private Boolean trialEnabled;
    private Integer trialDays;

    // Usage
    private Integer usageLimit;
    private Integer currentUsage;
    private String resetFrequency;
    private LocalDateTime lastResetAt;

    // Configuration
    private Map<String, Object> config;
    private Map<String, Object> metadata;

    // Rollout
    private Integer rolloutPercentage;
    private String rolloutGroup;

    // Tracking
    private LocalDateTime firstEnabledAt;
    private LocalDateTime lastEnabledAt;
    private Integer totalEnabledDays;
    private Integer toggleCount;

    // Audit
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
