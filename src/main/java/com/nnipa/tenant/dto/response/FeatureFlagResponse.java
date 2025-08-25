package com.nnipa.tenant.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Feature flag response")
public class FeatureFlagResponse {

    @Schema(description = "Feature flag ID")
    private UUID id;

    @Schema(description = "Feature code")
    private String featureCode;

    @Schema(description = "Feature name")
    private String featureName;

    @Schema(description = "Description")
    private String description;

    @Schema(description = "Category")
    private String category;

    @Schema(description = "Is enabled")
    private Boolean isEnabled;

    @Schema(description = "Is accessible (considering all constraints)")
    private Boolean isAccessible;

    @Schema(description = "Is beta feature")
    private Boolean isBeta;

    @Schema(description = "Is experimental")
    private Boolean isExperimental;

    @Schema(description = "Required plan")
    private String requiredPlan;

    @Schema(description = "Trial enabled")
    private Boolean trialEnabled;

    @Schema(description = "Trial days remaining")
    private Integer trialDaysRemaining;

    @Schema(description = "Usage limit")
    private Integer usageLimit;

    @Schema(description = "Current usage")
    private Integer currentUsage;

    @Schema(description = "Usage percentage")
    private Integer usagePercentage;

    @Schema(description = "Enabled from")
    private Instant enabledFrom;

    @Schema(description = "Enabled until")
    private Instant enabledUntil;

    @Schema(description = "Rollout percentage")
    private Integer rolloutPercentage;

    @Schema(description = "Rollout group")
    private String rolloutGroup;
}
