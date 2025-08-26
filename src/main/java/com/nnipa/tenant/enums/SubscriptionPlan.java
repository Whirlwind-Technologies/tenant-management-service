package com.nnipa.tenant.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import java.math.BigDecimal;

/**
 * Subscription plans available on the NNIPA platform.
 * Different plans offer varying features, resources, and pricing models.
 */
@Getter
@RequiredArgsConstructor
public enum SubscriptionPlan {

    FREEMIUM(
            "Freemium",
            "Free tier for individuals and small projects",
            BigDecimal.ZERO,
            1, // max users
            10, // max projects
            100, // storage in GB
            1000, // API calls per day
            false, // advanced analytics
            false, // priority support
            0, // compute units per month
            0 // trial days
    ),

    BASIC(
            "Basic",
            "Entry-level plan for small organizations",
            new BigDecimal("49.99"),
            5,
            25,
            500,
            10000,
            false,
            false,
            100,
            14 // 14-day trial
    ),

    PROFESSIONAL(
            "Professional",
            "Standard plan for growing organizations",
            new BigDecimal("299.99"),
            25,
            100,
            2000,
            100000,
            true,
            true,
            1000,
            30 // 30-day trial
    ),

    ENTERPRISE(
            "Enterprise",
            "Full-featured plan for large organizations",
            new BigDecimal("999.99"),
            -1, // unlimited users
            -1, // unlimited projects
            10000,
            -1, // unlimited API calls
            true,
            true,
            10000,
            30 // 30-day trial
    ),

    GOVERNMENT(
            "Government",
            "Specialized plan for government agencies",
            new BigDecimal("2999.99"),
            -1,
            -1,
            50000,
            -1,
            true,
            true,
            50000,
            60 // 60-day trial for government evaluation
    ),

    ACADEMIC(
            "Academic",
            "Discounted plan for educational institutions",
            new BigDecimal("149.99"),
            100,
            500,
            5000,
            500000,
            true,
            true,
            5000,90 // Extended 90-day trial for academic year planning
    ),

    CUSTOM(
            "Custom",
            "Tailored plan with negotiated terms",
            null, // Custom pricing
            -1,
            -1,
            -1,
            -1,
            true,
            true,
            -1,
            0 //Custom negotiation for trial
    ),

    TRIAL(
            "Trial",
            "Full-featured trial for evaluation",
            BigDecimal.ZERO,
            10,
            50,
            1000,
            50000,
            true,
            true,
            500,
            30 // 30-day standard trial
    );

    private final String displayName;
    private final String description;
    private final BigDecimal baseMonthlyPrice; // null for custom pricing
    private final int maxUsers; // -1 for unlimited
    private final int maxProjects; // -1 for unlimited
    private final int storageGb; // -1 for unlimited
    private final int apiCallsPerDay; // -1 for unlimited
    private final boolean hasAdvancedAnalytics;
    private final boolean hasPrioritySupport;
    private final int computeUnitsPerMonth; // -1 for unlimited
    private final int trialDays;

    /**
     * Checks if this plan supports a specific number of users.
     */
    public boolean supportsUserCount(int userCount) {
        return maxUsers == -1 || userCount <= maxUsers;
    }

    /**
     * Checks if this plan has a specific feature.
     */
    public boolean hasFeature(String feature) {
        return switch (feature) {
            case "ADVANCED_ANALYTICS" -> hasAdvancedAnalytics;
            case "PRIORITY_SUPPORT" -> hasPrioritySupport;
            case "API_ACCESS" -> apiCallsPerDay > 0;
            case "CUSTOM_BRANDING" -> this == ENTERPRISE || this == GOVERNMENT || this == CUSTOM;
            case "SSO" -> this == ENTERPRISE || this == GOVERNMENT || this == ACADEMIC || this == CUSTOM;
            case "AUDIT_LOGS" -> this != FREEMIUM && this != BASIC;
            case "DATA_EXPORT" -> this != FREEMIUM;
            default -> false;
        };
    }

    /**
     * Gets the SLA uptime percentage for this plan.
     */
    public double getSlaUptime() {
        return switch (this) {
            case GOVERNMENT -> 99.99;
            case ENTERPRISE, CUSTOM -> 99.95;
            case PROFESSIONAL, ACADEMIC -> 99.9;
            case BASIC, TRIAL -> 99.5;
            case FREEMIUM -> 99.0;
        };
    }

    /**
     * Gets the support response time in hours.
     */
    public int getSupportResponseHours() {
        return switch (this) {
            case GOVERNMENT, CUSTOM -> 1;
            case ENTERPRISE -> 2;
            case PROFESSIONAL, ACADEMIC -> 4;
            case BASIC -> 24;
            case TRIAL -> 48;
            case FREEMIUM -> 72;
        };
    }

    /**
     * Determines if this plan can be upgraded to another plan.
     */
    public boolean canUpgradeTo(SubscriptionPlan targetPlan) {
        if (this == targetPlan) return false;
        if (this == CUSTOM || targetPlan == CUSTOM) return false; // Custom requires negotiation
        if (this == TRIAL) return targetPlan != FREEMIUM; // Trial can upgrade to any paid plan

        // Define upgrade paths
        return switch (this) {
            case FREEMIUM -> true; // Can upgrade to any plan
            case BASIC -> targetPlan != FREEMIUM;
            case PROFESSIONAL -> targetPlan == ENTERPRISE || targetPlan == GOVERNMENT;
            case ACADEMIC -> targetPlan == ENTERPRISE || targetPlan == GOVERNMENT;
            default -> false;
        };
    }


}
