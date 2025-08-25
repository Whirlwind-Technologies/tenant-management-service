package com.nnipa.tenant.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Tenant status in the NNIPA platform lifecycle.
 */
@Getter
@RequiredArgsConstructor
public enum TenantStatus {

    PENDING_VERIFICATION(
            "Pending Verification",
            "Tenant registration submitted, awaiting verification",
            false,
            false
    ),

    ACTIVE(
            "Active",
            "Tenant is active and fully operational",
            true,
            true
    ),

    TRIAL(
            "Trial",
            "Tenant is in trial period",
            true,
            true
    ),

    SUSPENDED(
            "Suspended",
            "Tenant temporarily suspended due to policy violation or payment issue",
            false,
            true
    ),

    INACTIVE(
            "Inactive",
            "Tenant voluntarily deactivated",
            false,
            true
    ),

    EXPIRED(
            "Expired",
            "Tenant subscription or trial has expired",
            false,
            true
    ),

    PENDING_DELETION(
            "Pending Deletion",
            "Tenant marked for deletion, in grace period",
            false,
            true
    ),

    DELETED(
            "Deleted",
            "Tenant has been permanently deleted",
            false,
            false
    ),

    MIGRATING(
            "Migrating",
            "Tenant data is being migrated",
            false,
            true
    ),

    LOCKED(
            "Locked",
            "Tenant locked due to security concern",
            false,
            true
    );

    private final String displayName;
    private final String description;
    private final boolean allowsLogin;
    private final boolean retainsData;

    /**
     * Checks if transition to another status is valid.
     */
    public boolean canTransitionTo(TenantStatus newStatus) {
        if (this == newStatus) return false;

        return switch (this) {
            case PENDING_VERIFICATION ->
                    newStatus == ACTIVE ||
                            newStatus == TRIAL ||
                            newStatus == DELETED;

            case ACTIVE ->
                    newStatus == SUSPENDED ||
                            newStatus == INACTIVE ||
                            newStatus == EXPIRED ||
                            newStatus == PENDING_DELETION ||
                            newStatus == MIGRATING ||
                            newStatus == LOCKED;

            case TRIAL ->
                    newStatus == ACTIVE ||
                            newStatus == EXPIRED ||
                            newStatus == SUSPENDED ||
                            newStatus == DELETED;

            case SUSPENDED ->
                    newStatus == ACTIVE ||
                            newStatus == PENDING_DELETION ||
                            newStatus == DELETED;

            case INACTIVE ->
                    newStatus == ACTIVE ||
                            newStatus == PENDING_DELETION ||
                            newStatus == DELETED;

            case EXPIRED ->
                    newStatus == ACTIVE ||
                            newStatus == PENDING_DELETION ||
                            newStatus == DELETED;

            case PENDING_DELETION ->
                    newStatus == ACTIVE ||
                            newStatus == DELETED;

            case DELETED -> false; // Cannot transition from deleted

            case MIGRATING ->
                    newStatus == ACTIVE ||
                            newStatus == SUSPENDED;

            case LOCKED ->
                    newStatus == ACTIVE ||
                            newStatus == SUSPENDED ||
                            newStatus == DELETED;
        };
    }

    /**
     * Determines if this status requires immediate attention.
     */
    public boolean requiresAttention() {
        return this == SUSPENDED ||
                this == EXPIRED ||
                this == PENDING_DELETION ||
                this == LOCKED;
    }

    /**
     * Gets the maximum days a tenant can remain in this status.
     */
    public Integer getMaxDurationDays() {
        return switch (this) {
            case PENDING_VERIFICATION -> 7;
            case TRIAL -> 30;
            case SUSPENDED -> 30;
            case EXPIRED -> 30;
            case PENDING_DELETION -> 30;
            case MIGRATING -> 1;
            default -> null; // No limit
        };
    }
}
