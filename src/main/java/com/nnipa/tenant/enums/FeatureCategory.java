package com.nnipa.tenant.enums;

import lombok.Getter;

/**
 * Feature Category Enum
 */
@Getter
public enum FeatureCategory {
    ANALYTICS("Analytics"),
    SECURITY("Security"),
    INTEGRATION("Integration"),
    UI("User Interface"),
    DATA("Data Management"),
    REPORTING("Reporting"),
    COLLABORATION("Collaboration"),
    API("API Access"),
    EXPERIMENTAL("Experimental"),
    PREMIUM("Premium");

    private final String displayName;

    FeatureCategory(String displayName) {
        this.displayName = displayName;
    }

}
