package com.nnipa.tenant.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID; /**
 * Response DTO for usage records
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UsageRecordResponse {

    private UUID id;
    private UUID subscriptionId;
    private LocalDateTime usageDate;

    private String metricName;
    private String metricCategory;

    private BigDecimal quantity;
    private String unit;
    private BigDecimal rate;
    private BigDecimal amount;

    private Boolean isBillable;
    private Boolean isOverage;
    private BigDecimal includedQuantity;
    private BigDecimal overageQuantity;

    private String description;
    private Map<String, Object> metadata;

    private LocalDateTime recordedAt;
    private LocalDateTime billedAt;
    private String invoiceId;
}
