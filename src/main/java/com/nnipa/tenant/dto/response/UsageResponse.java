package com.nnipa.tenant.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Usage record response")
public class UsageResponse {

    @Schema(description = "Usage record ID")
    private UUID id;

    @Schema(description = "Usage date")
    private LocalDate usageDate;

    @Schema(description = "Metric name")
    private String metricName;

    @Schema(description = "Metric category")
    private String metricCategory;

    @Schema(description = "Quantity")
    private BigDecimal quantity;

    @Schema(description = "Unit")
    private String unit;

    @Schema(description = "Rate")
    private BigDecimal rate;

    @Schema(description = "Amount")
    private BigDecimal amount;

    @Schema(description = "Is billable")
    private Boolean isBillable;

    @Schema(description = "Is overage")
    private Boolean isOverage;

    @Schema(description = "Overage quantity")
    private BigDecimal overageQuantity;

    @Schema(description = "Description")
    private String description;

    @Schema(description = "Recorded at")
    private Instant recordedAt;

    @Schema(description = "Billed at")
    private Instant billedAt;

    @Schema(description = "Invoice ID")
    private String invoiceId;
}

