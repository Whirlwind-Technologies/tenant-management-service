package com.nnipa.tenant.dto.request;

import com.nnipa.tenant.enums.SubscriptionPlan;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request for creating a subscription")
public class CreateSubscriptionRequest {

    @NotNull(message = "Tenant ID is required")
    @Schema(description = "Tenant ID")
    private UUID tenantId;

    @NotNull(message = "Plan is required")
    @Schema(description = "Subscription plan")
    private SubscriptionPlan plan;

    @Schema(description = "Billing cycle", example = "MONTHLY")
    private String billingCycle;

    @Schema(description = "Payment method", example = "CREDIT_CARD")
    private String paymentMethod;

    @Schema(description = "Auto-renew enabled", defaultValue = "true")
    private Boolean autoRenew;

    @Schema(description = "Start with trial", defaultValue = "false")
    private Boolean startTrial;

    @Valid
    @Schema(description = "Billing details")
    private BillingDetailsRequest billingDetails;
}