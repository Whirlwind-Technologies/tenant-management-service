package com.nnipa.tenant.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request for recording usage")
public class RecordUsageRequest {

    @NotNull(message = "Metric name is required")
    @Schema(description = "Metric name", example = "API_CALLS")
    private String metricName;

    @NotNull(message = "Quantity is required")
    @Positive(message = "Quantity must be positive")
    @Schema(description = "Usage quantity", example = "1000")
    private BigDecimal quantity;

    @NotNull(message = "Unit is required")
    @Schema(description = "Unit of measurement", example = "CALLS")
    private String unit;

    @Schema(description = "Metric category", example = "API")
    private String metricCategory;

    @Schema(description = "Description")
    private String description;

    @Schema(description = "Is billable", defaultValue = "true")
    private Boolean isBillable = true;
}