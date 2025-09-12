package com.nnipa.tenant.enums;

import lombok.Getter;

/**
 * Subscription Plan Enum
 */
@Getter
public enum SubscriptionPlan {
    FREEMIUM("Freemium", 0),
    BASIC("Basic", 1),
    PROFESSIONAL("Professional", 2),
    ENTERPRISE("Enterprise", 3),
    GOVERNMENT("Government", 4),
    ACADEMIC("Academic", 2),
    CUSTOM("Custom", 5),
    TRIAL("Trial", 0);

    private final String displayName;
    private final int tier;

    SubscriptionPlan(String displayName, int tier) {
        this.displayName = displayName;
        this.tier = tier;
    }

}
