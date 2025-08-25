package com.nnipa.tenant.dto.response;

import com.nnipa.tenant.enums.SubscriptionPlan;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Subscription information response")
public class SubscriptionResponse {

    @Schema(description = "Subscription ID")
    private UUID id;

    @Schema(description = "Tenant ID")
    private UUID tenantId;

    @Schema(description = "Tenant name")
    private String tenantName;

    @Schema(description = "Subscription plan")
    private SubscriptionPlan plan;

    @Schema(description = "Subscription status")
    private String subscriptionStatus;

    @Schema(description = "Monthly price")
    private BigDecimal monthlyPrice;

    @Schema(description = "Currency")
    private String currency;

    @Schema(description = "Billing cycle")
    private String billingCycle;

    @Schema(description = "Discount percentage")
    private BigDecimal discountPercentage;

    @Schema(description = "Discount reason")
    private String discountReason;

    @Schema(description = "Start date")
    private Instant startDate;

    @Schema(description = "End date")
    private Instant endDate;

    @Schema(description = "Trial end date")
    private Instant trialEndDate;

    @Schema(description = "Next renewal date")
    private Instant nextRenewalDate;

    @Schema(description = "Auto-renew enabled")
    private Boolean autoRenew;

    @Schema(description = "Payment method")
    private String paymentMethod;

    @Schema(description = "Custom features")
    private Set<String> customFeatures;

    @Schema(description = "Add-ons")
    private Set<String> addons;

    @Schema(description = "Is in trial")
    private Boolean isInTrial;

    @Schema(description = "Days until renewal")
    private Integer daysUntilRenewal;
}
