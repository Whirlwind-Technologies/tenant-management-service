package com.nnipa.tenant.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map; /**
 * Request DTO for recording usage
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UsageRecordRequest {

    @NotBlank
    private String metricName;

    private String metricCategory;

    @NotNull
    @DecimalMin("0.0")
    private BigDecimal quantity;

    private String unit;

    @DecimalMin("0.0")
    private BigDecimal rate;

    private String description;
    private Map<String, Object> metadata;

    private Boolean isBillable;
    private Boolean isOverage;
}
