package com.nnipa.tenant.service;

import com.nnipa.tenant.entity.Tenant;
import com.nnipa.tenant.entity.TenantSettings;
import com.nnipa.tenant.enums.OrganizationType;
import com.nnipa.tenant.enums.TenantStatus;
import com.nnipa.tenant.repository.TenantRepository;
import com.nnipa.tenant.repository.TenantSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Core service for tenant management operations.
 * Handles CRUD operations and tenant lifecycle management.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TenantService {

    private final TenantRepository tenantRepository;
    private final TenantSettingsRepository tenantSettingsRepository;
    private final TenantProvisioningService provisioningService;
    private final OrganizationClassificationService classificationService;

    /**
     * Creates a new tenant with appropriate configuration.
     */
    @Transactional
    @CacheEvict(value = "tenants", allEntries = true)
    public Tenant createTenant(Tenant tenant) {
        log.info("Creating new tenant: {} ({})", tenant.getName(), tenant.getOrganizationType());

        // Validate tenant code uniqueness
        if (tenantRepository.existsByTenantCode(tenant.getTenantCode())) {
            throw new TenantAlreadyExistsException("Tenant code already exists: " + tenant.getTenantCode());
        }

        // Classify organization and set defaults
        tenant = classificationService.classifyAndEnrichTenant(tenant);

        // Set initial status based on organization type
        if (tenant.getStatus() == null) {
            tenant.setStatus(determineInitialStatus(tenant.getOrganizationType()));
        }

        // Save tenant
        tenant = tenantRepository.save(tenant);

        // Create default settings
        TenantSettings settings = createDefaultSettings(tenant);
        tenant.setSettings(settings);

        // Provision resources based on isolation strategy
        tenant = provisioningService.provisionTenant(tenant);

        log.info("Tenant created successfully: {} (ID: {})", tenant.getName(), tenant.getId());
        return tenant;
    }

    /**
     * Retrieves a tenant by ID.
     */
    @Cacheable(value = "tenants", key = "#id")
    public Optional<Tenant> getTenantById(UUID id) {
        log.debug("Fetching tenant by ID: {}", id);
        return tenantRepository.findById(id);
    }

    /**
     * Retrieves a tenant by tenant code.
     */
    @Cacheable(value = "tenants", key = "#tenantCode")
    public Optional<Tenant> getTenantByCode(String tenantCode) {
        log.debug("Fetching tenant by code: {}", tenantCode);
        return tenantRepository.findByTenantCode(tenantCode);
    }

    /**
     * Updates an existing tenant.
     */
    @Transactional
    @CacheEvict(value = "tenants", key = "#id")
    public Tenant updateTenant(UUID id, Tenant updates) {
        log.info("Updating tenant: {}", id);

        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new TenantNotFoundException("Tenant not found: " + id));

        // Update allowed fields
        if (updates.getName() != null) {
            tenant.setName(updates.getName());
        }
        if (updates.getDisplayName() != null) {
            tenant.setDisplayName(updates.getDisplayName());
        }
        if (updates.getDescription() != null) {
            tenant.setDescription(updates.getDescription());
        }
        if (updates.getOrganizationEmail() != null) {
            tenant.setOrganizationEmail(updates.getOrganizationEmail());
        }
        if (updates.getOrganizationPhone() != null) {
            tenant.setOrganizationPhone(updates.getOrganizationPhone());
        }
        if (updates.getOrganizationWebsite() != null) {
            tenant.setOrganizationWebsite(updates.getOrganizationWebsite());
        }

        // Update address
        updateAddress(tenant, updates);

        // Update branding
        updateBranding(tenant, updates);

        tenant = tenantRepository.save(tenant);
        log.info("Tenant updated successfully: {}", id);
        return tenant;
    }

    /**
     * Activates a tenant.
     */
    @Transactional
    @CacheEvict(value = "tenants", key = "#id")
    public Tenant activateTenant(UUID id) {
        log.info("Activating tenant: {}", id);

        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new TenantNotFoundException("Tenant not found: " + id));

        if (!tenant.getStatus().canTransitionTo(TenantStatus.ACTIVE)) {
            throw new InvalidTenantStateException(
                    "Cannot activate tenant in status: " + tenant.getStatus()
            );
        }

        tenant.setStatus(TenantStatus.ACTIVE);
        tenant.setActivatedAt(Instant.now());
        tenant.setIsVerified(true);
        tenant.setVerifiedAt(Instant.now());

        tenant = tenantRepository.save(tenant);
        log.info("Tenant activated: {}", id);
        return tenant;
    }

    /**
     * Suspends a tenant.
     */
    @Transactional
    @CacheEvict(value = "tenants", key = "#id")
    public Tenant suspendTenant(UUID id, String reason) {
        log.info("Suspending tenant: {} (Reason: {})", id, reason);

        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new TenantNotFoundException("Tenant not found: " + id));

        if (!tenant.getStatus().canTransitionTo(TenantStatus.SUSPENDED)) {
            throw new InvalidTenantStateException(
                    "Cannot suspend tenant in status: " + tenant.getStatus()
            );
        }

        tenant.suspend(reason);
        tenant = tenantRepository.save(tenant);

        log.info("Tenant suspended: {}", id);
        return tenant;
    }

    /**
     * Marks a tenant for deletion.
     */
    @Transactional
    @CacheEvict(value = "tenants", key = "#id")
    public Tenant markForDeletion(UUID id) {
        log.info("Marking tenant for deletion: {}", id);

        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new TenantNotFoundException("Tenant not found: " + id));

        tenant.setStatus(TenantStatus.PENDING_DELETION);
        tenant = tenantRepository.save(tenant);

        log.info("Tenant marked for deletion: {}", id);
        return tenant;
    }

    /**
     * Permanently deletes a tenant.
     */
    @Transactional
    @CacheEvict(value = "tenants", key = "#id")
    public void deleteTenant(UUID id) {
        log.info("Deleting tenant: {}", id);

        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new TenantNotFoundException("Tenant not found: " + id));

        // Only allow deletion if in PENDING_DELETION status
        if (tenant.getStatus() != TenantStatus.PENDING_DELETION) {
            throw new InvalidTenantStateException(
                    "Tenant must be in PENDING_DELETION status to delete"
            );
        }

        // Deprovision resources
        provisioningService.deprovisionTenant(tenant);

        // Soft delete
        tenant.softDelete("SYSTEM");
        tenant.setStatus(TenantStatus.DELETED);
        tenantRepository.save(tenant);

        log.info("Tenant deleted: {}", id);
    }

    /**
     * Gets all active tenants.
     */
    public List<Tenant> getActiveTenants() {
        log.debug("Fetching all active tenants");
        return tenantRepository.findActiveTenants();
    }

    /**
     * Gets tenants by organization type.
     */
    public List<Tenant> getTenantsByOrganizationType(OrganizationType type) {
        log.debug("Fetching tenants by organization type: {}", type);
        return tenantRepository.findByOrganizationType(type);
    }

    /**
     * Gets tenants expiring soon.
     */
    public List<Tenant> getExpiringTenants(int daysAhead) {
        log.debug("Fetching tenants expiring in {} days", daysAhead);
        Instant now = Instant.now();
        Instant future = now.plusSeconds(daysAhead * 24L * 60 * 60);
        return tenantRepository.findExpiringTenants(now, future);
    }

    /**
     * Gets trials ending soon.
     */
    public List<Tenant> getTrialsEndingSoon(int daysAhead) {
        log.debug("Fetching trials ending in {} days", daysAhead);
        Instant now = Instant.now();
        Instant future = now.plusSeconds(daysAhead * 24L * 60 * 60);
        return tenantRepository.findTrialEndingSoon(now, future);
    }

    /**
     * Searches tenants with pagination.
     */
    public Page<Tenant> searchTenants(String query, Pageable pageable) {
        log.debug("Searching tenants with query: {}", query);
        // Implement search logic based on requirements
        return tenantRepository.findAll(pageable);
    }

    /**
     * Verifies a tenant.
     */
    @Transactional
    @CacheEvict(value = "tenants", key = "#id")
    public Tenant verifyTenant(UUID id, String verifiedBy, String verificationDocument) {
        log.info("Verifying tenant: {}", id);

        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new TenantNotFoundException("Tenant not found: " + id));

        tenant.setIsVerified(true);
        tenant.setVerifiedAt(Instant.now());
        tenant.setVerifiedBy(verifiedBy);
        tenant.setVerificationDocument(verificationDocument);

        // Auto-activate if in pending verification
        if (tenant.getStatus() == TenantStatus.PENDING_VERIFICATION) {
            tenant.setStatus(TenantStatus.ACTIVE);
            tenant.setActivatedAt(Instant.now());
        }

        tenant = tenantRepository.save(tenant);
        log.info("Tenant verified: {}", id);
        return tenant;
    }

    /**
     * Gets tenant statistics.
     */
    public TenantStatistics getTenantStatistics() {
        log.debug("Calculating tenant statistics");

        long totalTenants = tenantRepository.count();
        long activeTenants = tenantRepository.countActiveTenants();
        List<Object[]> statsByType = tenantRepository.getTenantStatisticsByOrgType();

        return TenantStatistics.builder()
                .totalTenants(totalTenants)
                .activeTenants(activeTenants)
                .statisticsByType(statsByType)
                .build();
    }

    // Helper methods

    private TenantStatus determineInitialStatus(OrganizationType type) {
        return switch (type) {
            case INDIVIDUAL -> TenantStatus.TRIAL;
            case GOVERNMENT_AGENCY, FINANCIAL_INSTITUTION, HEALTHCARE ->
                    TenantStatus.PENDING_VERIFICATION;
            default -> TenantStatus.ACTIVE;
        };
    }

    private TenantSettings createDefaultSettings(Tenant tenant) {
        TenantSettings settings = TenantSettings.builder()
                .tenant(tenant)
                .defaultLanguage("en")
                .dateFormat("yyyy-MM-dd")
                .timeFormat("HH:mm:ss")
                .sessionTimeoutMinutes(getDefaultSessionTimeout(tenant.getOrganizationType()))
                .mfaRequired(requiresMFA(tenant.getOrganizationType()))
                .dataRetentionDays(tenant.getOrganizationType().getDefaultDataRetentionDays())
                .autoBackupEnabled(true)
                .backupFrequency(getDefaultBackupFrequency(tenant.getOrganizationType()))
                .enableApiAccess(true)
                .enableCustomReports(true)
                .build();

        return tenantSettingsRepository.save(settings);
    }

    private int getDefaultSessionTimeout(OrganizationType type) {
        return switch (type) {
            case GOVERNMENT_AGENCY, FINANCIAL_INSTITUTION, HEALTHCARE -> 15;
            case CORPORATION, ACADEMIC_INSTITUTION -> 30;
            default -> 60;
        };
    }

    private boolean requiresMFA(OrganizationType type) {
        return type == OrganizationType.GOVERNMENT_AGENCY ||
                type == OrganizationType.FINANCIAL_INSTITUTION ||
                type == OrganizationType.HEALTHCARE;
    }

    private String getDefaultBackupFrequency(OrganizationType type) {
        return switch (type) {
            case GOVERNMENT_AGENCY, FINANCIAL_INSTITUTION -> "HOURLY";
            case HEALTHCARE, CORPORATION -> "DAILY";
            default -> "WEEKLY";
        };
    }

    private void updateAddress(Tenant tenant, Tenant updates) {
        if (updates.getAddressLine1() != null) {
            tenant.setAddressLine1(updates.getAddressLine1());
        }
        if (updates.getAddressLine2() != null) {
            tenant.setAddressLine2(updates.getAddressLine2());
        }
        if (updates.getCity() != null) {
            tenant.setCity(updates.getCity());
        }
        if (updates.getStateProvince() != null) {
            tenant.setStateProvince(updates.getStateProvince());
        }
        if (updates.getPostalCode() != null) {
            tenant.setPostalCode(updates.getPostalCode());
        }
        if (updates.getCountry() != null) {
            tenant.setCountry(updates.getCountry());
        }
    }

    private void updateBranding(Tenant tenant, Tenant updates) {
        if (updates.getLogoUrl() != null) {
            tenant.setLogoUrl(updates.getLogoUrl());
        }
        if (updates.getPrimaryColor() != null) {
            tenant.setPrimaryColor(updates.getPrimaryColor());
        }
        if (updates.getSecondaryColor() != null) {
            tenant.setSecondaryColor(updates.getSecondaryColor());
        }
    }

    // Exceptions

    public static class TenantNotFoundException extends RuntimeException {
        public TenantNotFoundException(String message) {
            super(message);
        }
    }

    public static class TenantAlreadyExistsException extends RuntimeException {
        public TenantAlreadyExistsException(String message) {
            super(message);
        }
    }

    public static class InvalidTenantStateException extends RuntimeException {
        public InvalidTenantStateException(String message) {
            super(message);
        }
    }

    // Statistics DTO

    @lombok.Data
    @lombok.Builder
    public static class TenantStatistics {
        private long totalTenants;
        private long activeTenants;
        private List<Object[]> statisticsByType;
    }
}