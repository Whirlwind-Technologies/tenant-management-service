package com.nnipa.tenant.enums;

import lombok.Getter;

/**
 * Tenant Status Enum
 */
@Getter
public enum TenantStatus {
    PENDING("Pending Activation"),
    ACTIVE("Active"),
    INACTIVE("Inactive"),
    SUSPENDED("Suspended"),
    TRIAL("Trial Period"),
    EXPIRED("Expired"),
    CANCELLED("Cancelled"),
    PENDING_DELETION("Pending Deletion"),
    DELETED("Deleted"),
    MIGRATING("Migrating");

    private final String displayName;

    TenantStatus(String displayName) {
        this.displayName = displayName;
    }

}
