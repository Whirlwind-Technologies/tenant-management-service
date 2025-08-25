package com.nnipa.tenant.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import java.util.Set;

/**
 * Compliance frameworks supported by the NNIPA platform.
 * Different organizations require adherence to various regulatory standards.
 */
@Getter
@RequiredArgsConstructor
public enum ComplianceFramework {

    GDPR(
            "GDPR",
            "General Data Protection Regulation",
            "European Union data protection and privacy regulation",
            Set.of("DATA_ENCRYPTION", "RIGHT_TO_ERASURE", "DATA_PORTABILITY", "CONSENT_MANAGEMENT", "BREACH_NOTIFICATION")
    ),

    HIPAA(
            "HIPAA",
            "Health Insurance Portability and Accountability Act",
            "US healthcare data privacy and security provisions",
            Set.of("PHI_PROTECTION", "ACCESS_CONTROLS", "AUDIT_LOGS", "ENCRYPTION", "BREACH_NOTIFICATION", "BAA_REQUIRED")
    ),

    SOX(
            "SOX",
            "Sarbanes-Oxley Act",
            "US federal law for financial record keeping and reporting",
            Set.of("FINANCIAL_CONTROLS", "AUDIT_TRAILS", "DATA_RETENTION_7Y", "ACCESS_CONTROLS", "CHANGE_MANAGEMENT")
    ),

    FISMA(
            "FISMA",
            "Federal Information Security Management Act",
            "US federal government information security requirements",
            Set.of("RISK_ASSESSMENT", "SECURITY_CONTROLS", "CONTINUOUS_MONITORING", "INCIDENT_RESPONSE", "CONTINGENCY_PLANNING")
    ),

    FERPA(
            "FERPA",
            "Family Educational Rights and Privacy Act",
            "US education records privacy law",
            Set.of("EDUCATION_RECORDS_PROTECTION", "PARENT_ACCESS_RIGHTS", "CONSENT_REQUIREMENTS", "DIRECTORY_INFO_CONTROLS")
    ),

    CCPA(
            "CCPA",
            "California Consumer Privacy Act",
            "California state data privacy law",
            Set.of("CONSUMER_RIGHTS", "OPT_OUT", "DATA_DISCLOSURE", "NON_DISCRIMINATION", "PRIVACY_NOTICE")
    ),

    PCI_DSS(
            "PCI-DSS",
            "Payment Card Industry Data Security Standard",
            "Security standards for payment card data",
            Set.of("CARD_DATA_PROTECTION", "NETWORK_SECURITY", "ACCESS_CONTROL", "REGULAR_TESTING", "SECURITY_POLICY")
    ),

    ISO_27001(
            "ISO 27001",
            "International Organization for Standardization 27001",
            "International information security management standard",
            Set.of("ISMS", "RISK_MANAGEMENT", "SECURITY_CONTROLS", "CONTINUOUS_IMPROVEMENT", "CERTIFICATION")
    ),

    NIST(
            "NIST",
            "National Institute of Standards and Technology",
            "US federal agency guidelines and standards",
            Set.of("CYBERSECURITY_FRAMEWORK", "RISK_MANAGEMENT", "SECURITY_CONTROLS", "PRIVACY_FRAMEWORK", "SUPPLY_CHAIN")
    ),

    FedRAMP(
            "FedRAMP",
            "Federal Risk and Authorization Management Program",
            "US government cloud security assessment program",
            Set.of("CLOUD_SECURITY", "CONTINUOUS_MONITORING", "AUTHORIZATION", "SECURITY_CONTROLS", "THIRD_PARTY_ASSESSMENT")
    );

    private final String code;
    private final String fullName;
    private final String description;
    private final Set<String> requirements;

    /**
     * Checks if this framework requires a specific security feature.
     */
    public boolean requiresFeature(String feature) {
        return requirements.contains(feature);
    }

    /**
     * Determines if this framework is compatible with another.
     */
    public boolean isCompatibleWith(ComplianceFramework other) {
        // Some frameworks have conflicting requirements
        if (this == other) return true;

        // Check for known incompatibilities
        if ((this == GDPR && other == CCPA) || (this == CCPA && other == GDPR)) {
            return false; // Different data handling requirements
        }

        return true; // Most frameworks can coexist
    }

    /**
     * Gets the minimum data retention period in days required by this framework.
     */
    public int getMinDataRetentionDays() {
        return switch (this) {
            case SOX -> 2555; // 7 years
            case HIPAA -> 2190; // 6 years
            case FISMA -> 1095; // 3 years
            case PCI_DSS -> 365; // 1 year
            default -> 90; // 3 months minimum
        };
    }

    /**
     * Determines if this framework requires external audit.
     */
    public boolean requiresExternalAudit() {
        return this == SOX ||
                this == PCI_DSS ||
                this == ISO_27001 ||
                this == FedRAMP ||
                this == HIPAA;
    }

    /**
     * Gets the audit frequency in days.
     */
    public int getAuditFrequencyDays() {
        return switch (this) {
            case SOX, PCI_DSS -> 365; // Annual
            case HIPAA, ISO_27001 -> 365; // Annual
            case FedRAMP -> 365; // Annual with continuous monitoring
            case FISMA -> 365; // Annual
            default -> 730; // Biennial
        };
    }
}
