package com.nnipa.tenant.enums;

import lombok.Getter;

/**
 * Billing Cycle Enum
 */
@Getter
public enum BillingCycle {
    MONTHLY("Monthly", 1),
    QUARTERLY("Quarterly", 3),
    SEMI_ANNUAL("Semi-Annual", 6),
    ANNUAL("Annual", 12);

    private final String displayName;
    private final int months;

    BillingCycle(String displayName, int months) {
        this.displayName = displayName;
        this.months = months;
    }

}
