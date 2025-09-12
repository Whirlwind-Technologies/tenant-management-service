package com.nnipa.tenant.enums;

import lombok.Getter;

/**
 * Organization Type Enum
 */
@Getter
public enum OrganizationType {
    GOVERNMENT("Government Agency"),
    CORPORATION("Corporation"),
    ACADEMIC_INSTITUTION("Academic Institution"),
    NON_PROFIT("Non-Profit Organization"),
    RESEARCH_ORGANIZATION("Research Organization"),
    INDIVIDUAL("Individual"),
    STARTUP("Startup"),
    HEALTHCARE("Healthcare Organization"),
    FINANCIAL_INSTITUTION("Financial Institution");

    private final String displayName;

    OrganizationType(String displayName) {
        this.displayName = displayName;
    }

}

