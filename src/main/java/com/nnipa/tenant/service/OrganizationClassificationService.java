package com.nnipa.tenant.service;

import com.nnipa.tenant.entity.Tenant;
import com.nnipa.tenant.entity.TenantSettings;
import com.nnipa.tenant.enums.ComplianceFramework;
import com.nnipa.tenant.enums.OrganizationType;
import com.nnipa.tenant.enums.TenantIsolationStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

/**
 * Service for automatic organization classification and configuration.
 * No longer handles rate limiting (handled by api-gateway).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizationClassificationService {

    /**
     * Auto-detects organization type based on various signals.
     */
    public OrganizationType detectOrganizationType(String email, String organizationName, String taxId) {
        log.debug("Detecting organization type for: {} ({})", organizationName, email);

        // Check email domain patterns
        if (email != null) {
            String domain = email.substring(email.indexOf("@") + 1).toLowerCase();

            if (domain.endsWith(".gov") || domain.endsWith(".mil")) {
                return OrganizationType.GOVERNMENT_AGENCY;
            }
            if (domain.endsWith(".edu") || domain.endsWith(".ac.uk")) {
                return OrganizationType.ACADEMIC_INSTITUTION;
            }
            if (domain.contains("hospital") || domain.contains("health") ||
                    domain.contains("medical") || domain.contains("clinic")) {
                return OrganizationType.HEALTHCARE;
            }
        }

        // Check organization name patterns
        if (organizationName != null) {
            String nameLower = organizationName.toLowerCase();

            if (nameLower.contains("university") || nameLower.contains("college") ||
                    nameLower.contains("institute") || nameLower.contains("academy")) {
                return OrganizationType.ACADEMIC_INSTITUTION;
            }
            if (nameLower.contains("hospital") || nameLower.contains("health") ||
                    nameLower.contains("medical") || nameLower.contains("clinic")) {
                return OrganizationType.HEALTHCARE;
            }
            if (nameLower.contains("bank") || nameLower.contains("financial") ||
                    nameLower.contains("investment") || nameLower.contains("insurance")) {
                return OrganizationType.FINANCIAL_INSTITUTION;
            }
            if (nameLower.contains("foundation") || nameLower.contains("charity") ||
                    nameLower.contains("nonprofit") || nameLower.contains("ngo")) {
                return OrganizationType.NON_PROFIT;
            }
            if (nameLower.contains("research") || nameLower.contains("laboratory") ||
                    nameLower.contains("r&d")) {
                return OrganizationType.RESEARCH_ORGANIZATION;
            }
            if (nameLower.contains("startup") || nameLower.contains("ventures")) {
                return OrganizationType.STARTUP;
            }
            if (nameLower.contains("department") || nameLower.contains("agency") ||
                    nameLower.contains("ministry")) {
                return OrganizationType.GOVERNMENT_AGENCY;
            }
        }

        // Check tax ID patterns (simplified example)
        if (taxId != null && taxId.startsWith("EIN")) {
            return OrganizationType.NON_PROFIT;
        }

        // Check if individual (simple heuristic)
        if (email != null && !email.contains("@company") &&
                (email.contains("gmail") || email.contains("yahoo") ||
                        email.contains("hotmail") || email.contains("outlook"))) {
            return OrganizationType.INDIVIDUAL;
        }

        // Default to corporation
        return OrganizationType.CORPORATION;
    }

    /**
     * Determines appropriate isolation strategy based on organization type.
     */
    public TenantIsolationStrategy determineIsolationStrategy(OrganizationType type) {
        return switch (type) {
            case GOVERNMENT_AGENCY, FINANCIAL_INSTITUTION ->
                    TenantIsolationStrategy.DATABASE_PER_TENANT;
            case HEALTHCARE ->
                    TenantIsolationStrategy.SCHEMA_PER_TENANT;
            case CORPORATION, ACADEMIC_INSTITUTION ->
                    TenantIsolationStrategy.HYBRID_POOL;
            case RESEARCH_ORGANIZATION, NON_PROFIT, STARTUP ->
                    TenantIsolationStrategy.SHARED_SCHEMA_ROW_LEVEL;
            case INDIVIDUAL ->
                    TenantIsolationStrategy.SHARED_SCHEMA_BASIC;
        };
    }

    /**
     * Assigns compliance frameworks based on organization type.
     */
    public Set<ComplianceFramework> assignComplianceFrameworks(OrganizationType type) {
        Set<ComplianceFramework> frameworks = new HashSet<>();
        frameworks.add(ComplianceFramework.GDPR); // Default for all

        switch (type) {
            case GOVERNMENT_AGENCY -> {
                frameworks.add(ComplianceFramework.FISMA);
                frameworks.add(ComplianceFramework.FedRAMP);
                frameworks.add(ComplianceFramework.NIST);
            }
            case HEALTHCARE -> {
                frameworks.add(ComplianceFramework.HIPAA);
            }
            case FINANCIAL_INSTITUTION -> {
                frameworks.add(ComplianceFramework.SOX);
                frameworks.add(ComplianceFramework.PCI_DSS);
                frameworks.add(ComplianceFramework.ISO_27001);
            }
            case ACADEMIC_INSTITUTION -> {
                frameworks.add(ComplianceFramework.FERPA);
            }
            case CORPORATION -> {
                frameworks.add(ComplianceFramework.SOX);
                frameworks.add(ComplianceFramework.ISO_27001);
            }
            default -> {
                // Only GDPR for others
            }
        }

        return frameworks;
    }

    /**
     * Configures organization-specific settings.
     */
    public void configureOrganizationDefaults(Tenant tenant, TenantSettings settings) {
        OrganizationType type = tenant.getOrganizationType();

        // Set compliance frameworks
        Set<ComplianceFramework> frameworks = assignComplianceFrameworks(type);
        tenant.setComplianceFrameworks(frameworks);

        // Configure data retention
        settings.setDataRetentionDays(getDefaultDataRetention(type));

        // Configure backup settings
        settings.setBackupFrequency(getDefaultBackupFrequency(type));
        settings.setBackupRetentionDays(getDefaultBackupRetention(type));

        // Configure export settings
        settings.setMaxExportRows(getDefaultMaxExportRows(type));

        // Configure resource limits
        configureResourceLimits(tenant, settings);

        log.info("Configured defaults for {} organization: {}",
                type, tenant.getName());
    }

    private int getDefaultDataRetention(OrganizationType type) {
        return switch (type) {
            case GOVERNMENT_AGENCY, FINANCIAL_INSTITUTION -> 2555; // 7 years
            case HEALTHCARE -> 2190; // 6 years
            case ACADEMIC_INSTITUTION -> 1095; // 3 years
            case CORPORATION, NON_PROFIT -> 730; // 2 years
            default -> 365; // 1 year
        };
    }

    private String getDefaultBackupFrequency(OrganizationType type) {
        return switch (type) {
            case GOVERNMENT_AGENCY, FINANCIAL_INSTITUTION, HEALTHCARE -> "HOURLY";
            case CORPORATION, ACADEMIC_INSTITUTION -> "DAILY";
            default -> "WEEKLY";
        };
    }

    private int getDefaultBackupRetention(OrganizationType type) {
        return switch (type) {
            case GOVERNMENT_AGENCY, FINANCIAL_INSTITUTION -> 90;
            case HEALTHCARE, CORPORATION -> 60;
            default -> 30;
        };
    }

    private int getDefaultMaxExportRows(OrganizationType type) {
        return switch (type) {
            case GOVERNMENT_AGENCY, CORPORATION -> 1000000;
            case FINANCIAL_INSTITUTION, ACADEMIC_INSTITUTION -> 500000;
            case HEALTHCARE, RESEARCH_ORGANIZATION -> 250000;
            default -> 100000;
        };
    }

    private void configureResourceLimits(Tenant tenant, TenantSettings settings) {
        OrganizationType type = tenant.getOrganizationType();

        // Storage quotas
        int storageGb = switch (type) {
            case GOVERNMENT_AGENCY -> 50000;
            case FINANCIAL_INSTITUTION -> 20000;
            case CORPORATION, HEALTHCARE -> 10000;
            case ACADEMIC_INSTITUTION -> 5000;
            case RESEARCH_ORGANIZATION -> 2000;
            case NON_PROFIT, STARTUP -> 500;
            case INDIVIDUAL -> 50;
        };

        settings.addCustomSetting("storageQuotaGb", storageGb);

        // Project limits
        int maxProjects = switch (type) {
            case GOVERNMENT_AGENCY -> 1000;
            case CORPORATION, FINANCIAL_INSTITUTION -> 500;
            case ACADEMIC_INSTITUTION, HEALTHCARE -> 200;
            case RESEARCH_ORGANIZATION -> 100;
            case NON_PROFIT, STARTUP -> 50;
            case INDIVIDUAL -> 10;
        };

        settings.addCustomSetting("maxProjects", maxProjects);
    }
}