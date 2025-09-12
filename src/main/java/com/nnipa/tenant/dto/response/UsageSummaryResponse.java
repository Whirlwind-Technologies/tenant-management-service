package com.nnipa.tenant.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal; /**
 * Aggregated usage response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UsageSummaryResponse {
    private String metricName;
    private BigDecimal totalQuantity;
    private BigDecimal totalAmount;
    private String unit;
    private Integer recordCount;
}
