package com.nnipa.tenant.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nnipa.tenant.enums.BillingCycle;
import com.nnipa.tenant.enums.SubscriptionPlan;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime; /**
 * Request DTO for subscription management
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UpdateSubscriptionRequest {

    private SubscriptionPlan plan;
    private BillingCycle billingCycle;

    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal monthlyPrice;

    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal annualPrice;

    private String currency;

    // Discount
    @DecimalMin(value = "0.0")
    @DecimalMax(value = "100.0")
    private BigDecimal discountPercentage;

    private BigDecimal discountAmount;
    private String discountReason;
    private LocalDateTime discountValidUntil;

    // Payment
    private String paymentMethod;
    private Boolean autoRenew;

    // External references
    private String stripeSubscriptionId;
    private String stripeCustomerId;
}
