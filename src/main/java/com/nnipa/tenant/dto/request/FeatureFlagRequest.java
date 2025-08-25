package com.nnipa.tenant.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Feature flag request")
public class FeatureFlagRequest {

    @NotBlank(message = "Feature code is required")
    @Schema(description = "Feature code")
    private String featureCode;

    @Schema(description = "Is enabled")
    private Boolean isEnabled;

    @Schema(description = "Trial days")
    private Integer trialDays;

    @Schema(description = "Usage limit")
    private Integer usageLimit;

    @Schema(description = "Reset frequency", example = "DAILY")
    private String resetFrequency;

    @Schema(description = "Configuration JSON")
    private String configJson;

    @Schema(description = "Rollout percentage")
    @Min(0) @Max(100)
    private Integer rolloutPercentage;

    @Schema(description = "Rollout group")
    private String rolloutGroup;
}