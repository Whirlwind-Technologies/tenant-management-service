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

    PROVISIONING(
            "Provisioning",
            "Tenant provisioning process started",
            false,
            true
    ),

    DATABASE_CREATED(
            "Database Created",
            "Tenant database has been created",
            false,
            true
    ),

    SCHEMA_CREATED(
            "Schema Created",
            "Tenant schema has been created",
            false,
            true
    ),

    READY(
            "Ready",
            "Tenant is ready for use (shared schema with row-level security, no provisioning needed)",
            true,
            true
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

    DEACTIVATED(
            "Deactivated",
            "Tenant voluntarily deactivated",
            false,
            true
    ),

    MARKED_FOR_DELETION(
            "Marked for Deletion",
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

    PROVISIONING_FAILED(
            "Provisioning Failed",
            "Tenant provisioning failed during setup",
            false,
            false
    ),

    CREATION_FAILED(
            "Creation Failed",
            "Tenant creation failed",
            false,
            false
    ),

    DEPROVISIONED(
            "Deprovisioned",
            "Tenant resources have been deprovisioned",
            false,
            false
    );

    private final String displayName;
    private final String description;
    private final boolean allowsLogin;
    private final boolean retainsData;

    /**
     * Checks if transition to another status is valid.
     */
    public boolean canTransitionTo(TenantStatus newStatus) {
        return switch (this) {
            case PENDING_VERIFICATION -> newStatus == PROVISIONING ||
                    newStatus == ACTIVE ||
                    newStatus == PROVISIONING_FAILED;
            case PROVISIONING -> newStatus == DATABASE_CREATED ||
                    newStatus == SCHEMA_CREATED ||
                    newStatus == READY ||
                    newStatus == PROVISIONING_FAILED;
            case DATABASE_CREATED, SCHEMA_CREATED, READY -> newStatus == ACTIVE ||
                    newStatus == TRIAL;
            case PROVISIONING_FAILED, CREATION_FAILED -> newStatus == PENDING_VERIFICATION ||
                    newStatus == PROVISIONING ||
                    newStatus == MARKED_FOR_DELETION;
            case ACTIVE -> newStatus == SUSPENDED ||
                    newStatus == DEACTIVATED ||
                    newStatus == MARKED_FOR_DELETION;
            case TRIAL -> newStatus == ACTIVE ||
                    newStatus == SUSPENDED ||
                    newStatus == DEACTIVATED;
            case SUSPENDED -> newStatus == ACTIVE ||
                    newStatus == DEACTIVATED ||
                    newStatus == MARKED_FOR_DELETION;
            case DEACTIVATED -> newStatus == ACTIVE ||
                    newStatus == MARKED_FOR_DELETION;
            case MARKED_FOR_DELETION -> newStatus == DELETED ||
                    newStatus == DEPROVISIONED;
            case DEPROVISIONED -> newStatus == DELETED;
            case DELETED -> false;
        };
    }
}
