package com.nnipa.tenant.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nnipa.tenant.enums.FeatureCategory;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map; /**
 * Request DTO for feature flag management
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FeatureFlagRequest {

    @NotBlank(message = "Feature code is required")
    @Pattern(regexp = "^[A-Z_]+$", message = "Feature code must be uppercase with underscores")
    private String featureCode;

    @NotBlank(message = "Feature name is required")
    private String featureName;

    private String description;
    private FeatureCategory category;

    @NotNull
    private Boolean isEnabled;

    private Boolean isBeta;
    private Boolean isExperimental;

    // Requirements
    private String requiredPlan;
    private String requiredOrganizationType;

    // Time-based access
    private LocalDateTime enabledFrom;
    private LocalDateTime enabledUntil;
    private Boolean trialEnabled;
    private Integer trialDays;

    // Usage limits
    @Min(0)
    private Integer usageLimit;
    private String resetFrequency;

    // Configuration
    private Map<String, Object> config;
    private Map<String, Object> metadata;

    // Rollout
    @Min(0)
    @Max(100)
    private Integer rolloutPercentage;
    private String rolloutGroup;
}
