package com.nnipa.tenant.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
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

    @NotBlank(message = "Metric name is required")
    @Schema(description = "Metric name", example = "API_CALLS")
    private String metricName;

    @NotNull(message = "Quantity is required")
    @Positive(message = "Quantity must be positive")
    @Schema(description = "Usage quantity")
    private BigDecimal quantity;

    @NotBlank(message = "Unit is required")
    @Schema(description = "Unit of measurement", example = "CALLS")
    private String unit;

    @Schema(description = "Description")
    private String description;

    @Schema(description = "Metadata")
    private String metadata;
}