package com.nnipa.tenant.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.nnipa.tenant.enums.BillingCycle;
import com.nnipa.tenant.enums.SubscriptionPlan;
import com.nnipa.tenant.enums.SubscriptionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID; /**
 * Response DTO for subscription information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SubscriptionResponse {

    private UUID id;
    private UUID tenantId;
    private String tenantCode;
    private String tenantName;

    private SubscriptionPlan plan;
    private SubscriptionStatus subscriptionStatus;

    // Pricing
    private BigDecimal monthlyPrice;
    private BigDecimal annualPrice;
    private String currency;
    private BillingCycle billingCycle;

    // Discount
    private BigDecimal discountPercentage;
    private BigDecimal discountAmount;
    private String discountReason;
    private LocalDateTime discountValidUntil;

    // Lifecycle
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private LocalDateTime trialStartDate;
    private LocalDateTime trialEndDate;
    private LocalDateTime cancelledAt;
    private String cancellationReason;

    // Renewal
    private LocalDateTime nextRenewalDate;
    private Boolean autoRenew;
    private LocalDateTime lastRenewedAt;

    // Payment
    private String paymentMethod;
    private LocalDateTime lastPaymentDate;
    private BigDecimal lastPaymentAmount;
    private String lastPaymentStatus;
    private Integer failedPaymentCount;

    // Billing details summary
    private BillingDetailsSummaryResponse billingDetails;

    // Usage summary
    private BigDecimal currentMonthUsage;
    private BigDecimal unbilledAmount;

    // Audit
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
