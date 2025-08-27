package com.nnipa.tenant.service;

import com.nnipa.tenant.entity.Tenant;
import com.nnipa.tenant.enums.*;
import com.nnipa.tenant.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantValidationService {

    public void validateOrganizationRequirements(Tenant tenant) {
        OrganizationType orgType = tenant.getOrganizationType();
        List<String> errors = new ArrayList<>();

        switch (orgType) {
            case GOVERNMENT_AGENCY -> validateGovernmentAgency(tenant, errors);
            case CORPORATION -> validateCorporation(tenant, errors);
            case ACADEMIC_INSTITUTION -> validateAcademicInstitution(tenant, errors);
            case NON_PROFIT -> validateNonProfit(tenant, errors);
            case RESEARCH_ORGANIZATION -> validateResearchOrg(tenant, errors);
            case INDIVIDUAL -> validateIndividual(tenant, errors);
            case STARTUP -> validateStartup(tenant, errors);
            case HEALTHCARE -> validateHealthcare(tenant, errors);
            case FINANCIAL_INSTITUTION -> validateFinancialInstitution(tenant, errors);
        }

        if (!errors.isEmpty()) {
            throw new ValidationException("Validation failed: " + String.join(", ", errors));
        }
    }

    private void validateGovernmentAgency(Tenant tenant, List<String> errors) {
        if (isEmpty(tenant.getTaxId())) {
            errors.add("Tax ID is required for government agencies");
        }
        if (!tenant.getTaxId().matches("^\\d{2}-\\d{7}$")) {
            errors.add("Invalid government tax ID format (expected: XX-XXXXXXX)");
        }
        if (isEmpty(tenant.getBusinessLicense())) {
            errors.add("Government authorization number required");
        }
        if (!tenant.getComplianceFrameworks().contains(ComplianceFramework.FISMA)) {
            tenant.getComplianceFrameworks().add(ComplianceFramework.FISMA);
        }
        tenant.setSecurityLevel("HIGH");
        tenant.setDataResidencyRegion("US");
    }

    private void validateCorporation(Tenant tenant, List<String> errors) {
        if (isEmpty(tenant.getTaxId())) {
            errors.add("Tax ID/EIN is required for corporations");
        }
        if (!tenant.getTaxId().matches("^\\d{2}-\\d{7}$|^\\d{9}$")) {
            errors.add("Invalid corporate tax ID format");
        }
        if (tenant.getMaxUsers() == null) {
            tenant.setMaxUsers(100);
        }
        if (tenant.getMaxProjects() == null) {
            tenant.setMaxProjects(50);
        }
    }

    private void validateAcademicInstitution(Tenant tenant, List<String> errors) {
        if (!tenant.getOrganizationEmail().matches(".*\\.edu$")) {
            log.warn("Academic institution without .edu email: {}", tenant.getName());
        }
        if (!tenant.getComplianceFrameworks().contains(ComplianceFramework.FERPA)) {
            tenant.getComplianceFrameworks().add(ComplianceFramework.FERPA);
        }
        // Apply academic discount
        if (tenant.getMetadata() == null) {
            tenant.setMetadata(new HashMap<>());
        }
        tenant.getMetadata().put("discount_percentage", "50");
        tenant.getMetadata().put("billing_cycle", "academic_year");
    }

    private void validateNonProfit(Tenant tenant, List<String> errors) {
        if (isEmpty(tenant.getTaxId())) {
            errors.add("501(c)(3) status number required for non-profits");
        }
        // Apply non-profit discount
        if (tenant.getMetadata() == null) {
            tenant.setMetadata(new HashMap<>());
        }
        tenant.getMetadata().put("discount_percentage", "30");
        tenant.getMetadata().put("tax_exempt", "true");
    }

    private void validateResearchOrg(Tenant tenant, List<String> errors) {
        // Set research-specific defaults
        if (tenant.getMaxProjects() == null) {
            tenant.setMaxProjects(100); // More projects for research
        }
        if (tenant.getStorageQuotaGb() == null) {
            tenant.setStorageQuotaGb(500); // More storage for datasets
        }
    }

    private void validateIndividual(Tenant tenant, List<String> errors) {
        // Individuals have minimal requirements
        tenant.setMaxUsers(1);
        tenant.setMaxProjects(5);
        tenant.setStorageQuotaGb(10);
        if (!tenant.getOrganizationEmail().contains("@")) {
            errors.add("Valid email required for individual accounts");
        }
    }

    private void validateStartup(Tenant tenant, List<String> errors) {
        // Apply startup benefits
        if (tenant.getMetadata() == null) {
            tenant.setMetadata(new HashMap<>());
        }
        tenant.getMetadata().put("discount_percentage", "25");
        tenant.getMetadata().put("trial_extension_days", "30");
        tenant.getMetadata().put("startup_program", "true");
    }

    private void validateHealthcare(Tenant tenant, List<String> errors) {
        if (!tenant.getComplianceFrameworks().contains(ComplianceFramework.HIPAA)) {
            tenant.getComplianceFrameworks().add(ComplianceFramework.HIPAA);
        }
        if (isEmpty(tenant.getBusinessLicense())) {
            errors.add("Healthcare facility license required");
        }
        tenant.setSecurityLevel("HIGH");
        // Enable PHI protection
        if (tenant.getMetadata() == null) {
            tenant.setMetadata(new HashMap<>());
        }
        tenant.getMetadata().put("phi_protection_enabled", "true");
        tenant.getMetadata().put("audit_retention_years", "6");
    }

    private void validateFinancialInstitution(Tenant tenant, List<String> errors) {
        if (isEmpty(tenant.getBusinessLicense())) {
            errors.add("Banking/financial license required");
        }
        if (!tenant.getComplianceFrameworks().contains(ComplianceFramework.SOX)) {
            tenant.getComplianceFrameworks().add(ComplianceFramework.SOX);
        }
        if (!tenant.getComplianceFrameworks().contains(ComplianceFramework.PCI_DSS)) {
            tenant.getComplianceFrameworks().add(ComplianceFramework.PCI_DSS);
        }
        tenant.setSecurityLevel("CRITICAL");
        tenant.getMetadata().put("transaction_audit_enabled", "true");
        tenant.getMetadata().put("retention_years", "7");
    }

    private boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }
}