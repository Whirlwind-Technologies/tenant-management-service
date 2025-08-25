package com.nnipa.tenant.dto.request;

import com.nnipa.tenant.enums.SubscriptionPlan;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request for changing subscription plan")
public class ChangePlanRequest {

    @NotNull(message = "New plan is required")
    @Schema(description = "New subscription plan")
    private SubscriptionPlan newPlan;

    @Schema(description = "Apply immediately", defaultValue = "false")
    private Boolean applyImmediately;

    @Schema(description = "Reason for change")
    private String reason;
}