package com.nnipa.tenant.enums;

import lombok.Getter;

/**
 * Subscription Status Enum
 */
@Getter
public enum SubscriptionStatus {
    PENDING("Pending"),
    ACTIVE("Active"),
    PAST_DUE("Past Due"),
    CANCELLED("Cancelled"),
    EXPIRED("Expired"),
    TRIALING("Trialing"),
    PAUSED("Paused"),
    UNPAID("Unpaid");

    private final String displayName;

    SubscriptionStatus(String displayName) {
        this.displayName = displayName;
    }

}
