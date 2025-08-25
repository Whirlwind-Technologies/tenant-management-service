package com.nnipa.tenant.service;

import com.nnipa.tenant.entity.Tenant;
import com.nnipa.tenant.enums.ComplianceFramework;
import com.nnipa.tenant.enums.OrganizationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Service for classifying organizations and automatically setting appropriate
 * configurations based on organization type and characteristics.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizationClassificationService {

    // Patterns for domain classification
    private static final Pattern GOV_DOMAIN = Pattern.compile(".*\\.(gov|mil|state\\.[a-z]{2}\\.us)$");
    private static final Pattern EDU_DOMAIN = Pattern.compile(".*\\.(edu|ac\\.[a-z]{2}|edu\\.[a-z]{2})$");
    private static final Pattern HEALTH_KEYWORDS = Pattern.compile(
            ".*(health|medical|clinic|hospital|pharma|healthcare).*",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern FINANCE_KEYWORDS = Pattern.compile(
            ".*(bank|finance|financial|investment|insurance|credit).*",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Classifies and enriches tenant based on available information.
     */
    public Tenant classifyAndEnrichTenant(Tenant tenant) {
        log.info("Classifying organization: {}", tenant.getName());

        // Auto-detect organization type if not set
        if (tenant.getOrganizationType() == null) {
            tenant.setOrganizationType(detectOrganizationType(tenant));
        }

        // Set compliance frameworks based on organization type
        enrichComplianceFrameworks(tenant);

        // Set security level
        tenant.setSecurityLevel(determineSecurityLevel(tenant));

        // Set data residency requirements
        if (tenant.getDataResidencyRegion() == null) {
            tenant.setDataResidencyRegion(determineDataResidency(tenant));
        }

        // Set resource limits based on organization type
        setDefaultResourceLimits(tenant);

        // Set default timezone and locale
        setDefaultLocalization(tenant);

        log.info("Organization classified as: {} with security level: {}",
                tenant.getOrganizationType(), tenant.getSecurityLevel());

        return tenant;
    }

    /**
     * Detects organization type based on email domain and other indicators.
     */
    private OrganizationType detectOrganizationType(Tenant tenant) {
        String email = tenant.getOrganizationEmail();
        String name = tenant.getName();
        String website = tenant.getOrganizationWebsite();

        // Check government domains
        if (email != null && GOV_DOMAIN.matcher(email).matches()) {
            return OrganizationType.GOVERNMENT_AGENCY;
        }

        // Check educational domains
        if (email != null && EDU_DOMAIN.matcher(email).matches()) {
            return OrganizationType.ACADEMIC_INSTITUTION;
        }

        // Check for healthcare indicators
        if (isHealthcareOrganization(name, website)) {
            return OrganizationType.HEALTHCARE;
        }

        // Check for financial indicators
        if (isFinancialOrganization(name, website)) {
            return OrganizationType.FINANCIAL_INSTITUTION;
        }

        // Check for non-profit indicators
        if (isNonProfit(tenant)) {
            return OrganizationType.NON_PROFIT;
        }

        // Check if individual (single user)
        if (tenant.getMaxUsers() != null && tenant.getMaxUsers() == 1) {
            return OrganizationType.INDIVIDUAL;
        }

        // Default to corporation
        return OrganizationType.CORPORATION;
    }

    /**
     * Enriches compliance frameworks based on organization type and location.
     */
    private void enrichComplianceFrameworks(Tenant tenant) {
        if (tenant.getComplianceFrameworks() == null) {
            tenant.setComplianceFrameworks(new HashSet<>());
        }

        Set<ComplianceFramework> frameworks = tenant.getComplianceFrameworks();
        OrganizationType type = tenant.getOrganizationType();
        String country = tenant.getCountry();

        // Add mandatory frameworks based on organization type
        switch (type) {
            case GOVERNMENT_AGENCY -> {
                frameworks.add(ComplianceFramework.FISMA);
                frameworks.add(ComplianceFramework.NIST);
                if ("US".equals(country)) {
                    frameworks.add(ComplianceFramework.FedRAMP);
                }
            }
            case HEALTHCARE -> {
                if ("US".equals(country)) {
                    frameworks.add(ComplianceFramework.HIPAA);
                }
                frameworks.add(ComplianceFramework.ISO_27001);
            }
            case FINANCIAL_INSTITUTION -> {
                frameworks.add(ComplianceFramework.SOX);
                frameworks.add(ComplianceFramework.PCI_DSS);
                frameworks.add(ComplianceFramework.ISO_27001);
            }
            case ACADEMIC_INSTITUTION -> {
                if ("US".equals(country)) {
                    frameworks.add(ComplianceFramework.FERPA);
                }
            }
            default -> {
                // No mandatory frameworks
            }
        }

        // Add regional compliance
        addRegionalCompliance(tenant, frameworks);
    }

    /**
     * Adds regional compliance requirements.
     */
    private void addRegionalCompliance(Tenant tenant, Set<ComplianceFramework> frameworks) {
        String country = tenant.getCountry();
        String state = tenant.getStateProvince();

        // GDPR for EU countries
        if (isEuropeanCountry(country)) {
            frameworks.add(ComplianceFramework.GDPR);
        }

        // CCPA for California
        if ("US".equals(country) && "CA".equals(state)) {
            frameworks.add(ComplianceFramework.CCPA);
        }

        // Add general ISO compliance for enterprises
        if (tenant.getOrganizationType() == OrganizationType.CORPORATION &&
                tenant.getMaxUsers() != null && tenant.getMaxUsers() > 100) {
            frameworks.add(ComplianceFramework.ISO_27001);
        }
    }

    /**
     * Determines security level based on organization type and compliance.
     */
    private String determineSecurityLevel(Tenant tenant) {
        OrganizationType type = tenant.getOrganizationType();
        int complianceCount = tenant.getComplianceFrameworks().size();

        // Maximum security for government and financial
        if (type == OrganizationType.GOVERNMENT_AGENCY ||
                type == OrganizationType.FINANCIAL_INSTITUTION) {
            return "MAXIMUM";
        }

        // Enhanced security for healthcare and high compliance
        if (type == OrganizationType.HEALTHCARE || complianceCount >= 3) {
            return "ENHANCED";
        }

        // Standard security for others
        return "STANDARD";
    }

    /**
     * Determines data residency requirements.
     */
    private String determineDataResidency(Tenant tenant) {
        String country = tenant.getCountry();
        OrganizationType type = tenant.getOrganizationType();

        // Government agencies require local data residency
        if (type == OrganizationType.GOVERNMENT_AGENCY) {
            return getDataRegionForCountry(country);
        }

        // Healthcare may require local residency
        if (type == OrganizationType.HEALTHCARE) {
            return getDataRegionForCountry(country);
        }

        // Default to nearest region
        return getDataRegionForCountry(country);
    }

    /**
     * Sets default resource limits based on organization type.
     */
    private void setDefaultResourceLimits(Tenant tenant) {
        OrganizationType type = tenant.getOrganizationType();

        if (tenant.getMaxUsers() == null) {
            tenant.setMaxUsers(type.getBasicTierMaxUsers());
        }

        if (tenant.getMaxProjects() == null) {
            tenant.setMaxProjects(getDefaultMaxProjects(type));
        }

        if (tenant.getStorageQuotaGb() == null) {
            tenant.setStorageQuotaGb(getDefaultStorageQuota(type));
        }

        if (tenant.getApiRateLimit() == null) {
            tenant.setApiRateLimit(getDefaultApiRateLimit(type));
        }
    }

    /**
     * Sets default localization settings.
     */
    private void setDefaultLocalization(Tenant tenant) {
        if (tenant.getTimezone() == null) {
            tenant.setTimezone(getTimezoneForCountry(tenant.getCountry()));
        }

        if (tenant.getLocale() == null) {
            tenant.setLocale(getLocaleForCountry(tenant.getCountry()));
        }
    }

    // Helper methods

    private boolean isHealthcareOrganization(String name, String website) {
        if (name != null && HEALTH_KEYWORDS.matcher(name).matches()) {
            return true;
        }
        if (website != null && HEALTH_KEYWORDS.matcher(website).matches()) {
            return true;
        }
        return false;
    }

    private boolean isFinancialOrganization(String name, String website) {
        if (name != null && FINANCE_KEYWORDS.matcher(name).matches()) {
            return true;
        }
        if (website != null && FINANCE_KEYWORDS.matcher(website).matches()) {
            return true;
        }
        return false;
    }

    private boolean isNonProfit(Tenant tenant) {
        String taxId = tenant.getTaxId();
        if (taxId != null && taxId.startsWith("501c")) {
            return true;
        }
        String name = tenant.getName();
        if (name != null && name.toLowerCase().contains("foundation")) {
            return true;
        }
        return false;
    }

    private boolean isEuropeanCountry(String country) {
        Set<String> euCountries = Set.of(
                "AT", "BE", "BG", "HR", "CY", "CZ", "DK", "EE", "FI", "FR",
                "DE", "GR", "HU", "IE", "IT", "LV", "LT", "LU", "MT", "NL",
                "PL", "PT", "RO", "SK", "SI", "ES", "SE"
        );
        return euCountries.contains(country);
    }

    private String getDataRegionForCountry(String country) {
        if (country == null) return "US-EAST";

        if (isEuropeanCountry(country)) return "EU";
        if (Set.of("US", "CA", "MX").contains(country)) return "US-EAST";
        if (Set.of("CN", "JP", "KR", "IN").contains(country)) return "ASIA";
        if (Set.of("AU", "NZ").contains(country)) return "APAC";

        return "US-EAST"; // Default
    }

    private String getTimezoneForCountry(String country) {
        if (country == null) return "UTC";

        return switch (country) {
            case "US" -> "America/New_York";
            case "GB" -> "Europe/London";
            case "FR" -> "Europe/Paris";
            case "DE" -> "Europe/Berlin";
            case "JP" -> "Asia/Tokyo";
            case "CN" -> "Asia/Shanghai";
            case "IN" -> "Asia/Kolkata";
            case "AU" -> "Australia/Sydney";
            default -> "UTC";
        };
    }

    private String getLocaleForCountry(String country) {
        if (country == null) return "en_US";

        return switch (country) {
            case "US" -> "en_US";
            case "GB" -> "en_GB";
            case "FR" -> "fr_FR";
            case "DE" -> "de_DE";
            case "ES" -> "es_ES";
            case "JP" -> "ja_JP";
            case "CN" -> "zh_CN";
            default -> "en_US";
        };
    }

    private int getDefaultMaxProjects(OrganizationType type) {
        return switch (type) {
            case GOVERNMENT_AGENCY -> 1000;
            case CORPORATION, FINANCIAL_INSTITUTION -> 500;
            case ACADEMIC_INSTITUTION, HEALTHCARE -> 200;
            case RESEARCH_ORGANIZATION -> 100;
            case NON_PROFIT, STARTUP -> 50;
            case INDIVIDUAL -> 10;
        };
    }

    private int getDefaultStorageQuota(OrganizationType type) {
        return switch (type) {
            case GOVERNMENT_AGENCY -> 50000; // 50TB
            case FINANCIAL_INSTITUTION -> 20000; // 20TB
            case CORPORATION, HEALTHCARE -> 10000; // 10TB
            case ACADEMIC_INSTITUTION -> 5000; // 5TB
            case RESEARCH_ORGANIZATION -> 2000; // 2TB
            case NON_PROFIT, STARTUP -> 500; // 500GB
            case INDIVIDUAL -> 50; // 50GB
        };
    }

    private int getDefaultApiRateLimit(OrganizationType type) {
        return switch (type) {
            case GOVERNMENT_AGENCY, FINANCIAL_INSTITUTION -> 100000; // per hour
            case CORPORATION, HEALTHCARE -> 50000;
            case ACADEMIC_INSTITUTION -> 25000;
            case RESEARCH_ORGANIZATION -> 10000;
            case NON_PROFIT, STARTUP -> 5000;
            case INDIVIDUAL -> 1000;
        };
    }
}