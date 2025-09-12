package com.nnipa.tenant.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.nnipa.tenant.enums.SubscriptionPlan;
import com.nnipa.tenant.enums.SubscriptionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID; /**
 * Summary response for subscription
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SubscriptionSummaryResponse {
    private UUID id;
    private SubscriptionPlan plan;
    private SubscriptionStatus status;
    private BigDecimal monthlyPrice;
    private String currency;
    private LocalDateTime nextRenewalDate;
    private Boolean autoRenew;
}
