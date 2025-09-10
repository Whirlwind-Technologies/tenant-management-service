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
    ),
    MIGRATING(
            "Migrating",
            "Tenant migrated",
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
    public boolean canTransitionTo(TenantStatus targetStatus) {
        // Define valid transitions
        return switch (this) {
            case PENDING_VERIFICATION -> targetStatus == PROVISIONING || targetStatus == ACTIVE;
            case PROVISIONING -> targetStatus == DATABASE_CREATED || targetStatus == PROVISIONING_FAILED;
            case DATABASE_CREATED -> targetStatus == SCHEMA_CREATED || targetStatus == PROVISIONING_FAILED;
            case SCHEMA_CREATED -> targetStatus == READY || targetStatus == PROVISIONING_FAILED;
            case READY -> targetStatus == ACTIVE || targetStatus == TRIAL;
            case ACTIVE -> targetStatus == SUSPENDED || targetStatus == MARKED_FOR_DELETION || targetStatus == MIGRATING;
            case TRIAL -> targetStatus == ACTIVE || targetStatus == SUSPENDED || targetStatus == MARKED_FOR_DELETION;
            case SUSPENDED -> targetStatus == ACTIVE || targetStatus == MARKED_FOR_DELETION;
            case MARKED_FOR_DELETION -> targetStatus == DELETED;
            case MIGRATING -> targetStatus == ACTIVE || targetStatus == PROVISIONING_FAILED;
            default -> false;
        };
    }
}
