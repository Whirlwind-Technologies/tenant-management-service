package com.nnipa.tenant.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Organization types supported by the NNIPA platform.
 * Each type has specific compliance requirements, features, and pricing models.
 */
@Getter
@RequiredArgsConstructor
public enum OrganizationType {

    GOVERNMENT_AGENCY(
            "Government Agency",
            "Federal, state, or local government organizations",
            true,
            true,
            100
    ),

    CORPORATION(
            "Corporation",
            "Private sector businesses and enterprises",
            false,
            true,
            80
    ),

    ACADEMIC_INSTITUTION(
            "Academic Institution",
            "Universities, colleges, and research institutions",
            false,
            true,
            60
    ),

    NON_PROFIT(
            "Non-Profit Organization",
            "501(c)(3) and other non-profit entities",
            false,
            false,
            40
    ),

    RESEARCH_ORGANIZATION(
            "Research Organization",
            "Independent research institutes and think tanks",
            false,
            true,
            70
    ),

    INDIVIDUAL(
            "Individual User",
            "Personal accounts for researchers and analysts",
            false,
            false,
            10
    ),

    STARTUP(
            "Startup",
            "Early-stage companies with special pricing",
            false,
            false,
            30
    ),

    HEALTHCARE(
            "Healthcare Organization",
            "Hospitals, clinics, and health systems requiring HIPAA compliance",
            true,
            true,
            90
    ),

    FINANCIAL_INSTITUTION(
            "Financial Institution",
            "Banks and financial services requiring SOX compliance",
            true,
            true,
            95
    );

    private final String displayName;
    private final String description;
    private final boolean requiresHighCompliance;
    private final boolean requiresAdvancedFeatures;
    private final int priorityScore; // Used for resource allocation and SLA

    /**
     * Determines if this organization type requires enhanced security measures.
     */
    public boolean requiresEnhancedSecurity() {
        return this == GOVERNMENT_AGENCY ||
                this == HEALTHCARE ||
                this == FINANCIAL_INSTITUTION;
    }

    /**
     * Determines if this organization type is eligible for academic pricing.
     */
    public boolean hasAcademicPricing() {
        return this == ACADEMIC_INSTITUTION ||
                this == NON_PROFIT ||
                this == RESEARCH_ORGANIZATION;
    }

    /**
     * Gets the default data retention period in days for this organization type.
     */
    public int getDefaultDataRetentionDays() {
        return switch (this) {
            case GOVERNMENT_AGENCY -> 2555; // 7 years
            case FINANCIAL_INSTITUTION -> 2555; // 7 years for SOX
            case HEALTHCARE -> 2190; // 6 years for HIPAA
            case CORPORATION -> 1095; // 3 years
            case ACADEMIC_INSTITUTION -> 1460; // 4 years
            default -> 365; // 1 year
        };
    }

    /**
     * Gets the maximum allowed users for the basic tier.
     */
    public int getBasicTierMaxUsers() {
        return switch (this) {
            case INDIVIDUAL -> 1;
            case STARTUP -> 10;
            case NON_PROFIT -> 25;
            case ACADEMIC_INSTITUTION -> 100;
            default -> 50;
        };
    }
}