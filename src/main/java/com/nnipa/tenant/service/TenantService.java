package com.nnipa.tenant.service;

import com.nnipa.tenant.client.NotificationServiceClient;
import com.nnipa.tenant.entity.Tenant;
import com.nnipa.tenant.entity.TenantSettings;
import com.nnipa.tenant.enums.ComplianceFramework;
import com.nnipa.tenant.enums.OrganizationType;
import com.nnipa.tenant.enums.TenantStatus;
import com.nnipa.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;
    private final OrganizationClassificationService classificationService;
    private final TenantSettingsService settingsService;
    private final NotificationServiceClient notificationClient;
    private final TenantProvisioningService provisioningService;

    @Transactional
    public Tenant createTenant(Tenant tenant) {
        log.info("Creating new tenant: {}", tenant.getName());

        try {
            // Step 1: Validate tenant data
            validateTenantCreation(tenant);

            // Step 2: Auto-classify organization type if not provided
            if (tenant.getOrganizationType() == null) {
                OrganizationType detectedType = classificationService.detectOrganizationType(
                        tenant.getOrganizationEmail(),
                        tenant.getName(),
                        tenant.getTaxId()
                );
                tenant.setOrganizationType(detectedType);
            }

            // Step 3: Set compliance frameworks based on organization type
            if (tenant.getComplianceFrameworks() == null || tenant.getComplianceFrameworks().isEmpty()) {
                tenant.setComplianceFrameworks(
                        classificationService.assignComplianceFrameworks(tenant.getOrganizationType())
                );
            }

            // Step 4: Set initial status
            tenant.setStatus(TenantStatus.PENDING_VERIFICATION);

            // Step 5: Generate unique tenant code if not provided
            if (tenant.getTenantCode() == null) {
                tenant.setTenantCode(generateTenantCode(tenant));
            }

            // Step 6: Provision the tenant (includes database/schema creation)
            tenant = provisioningService.provisionTenant(tenant);

            // Step 7: Create default settings
            TenantSettings settings = settingsService.createDefaultSettings(tenant);
            tenant.setSettings(settings);

            // Step 8: Send notification asynchronously
            sendTenantCreatedNotification(tenant);

            log.info("Tenant created successfully: {} (ID: {}, Status: {})",
                    tenant.getName(), tenant.getId(), tenant.getStatus());

            return tenant;

        } catch (DuplicateTenantException e) {
            log.error("Duplicate tenant detected: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Failed to create tenant: {}", tenant.getName(), e);

            // Attempt rollback if tenant was partially created
            if (tenant.getId() != null) {
                rollbackTenantCreation(tenant);
            }

            throw new TenantCreationException("Failed to create tenant: " + e.getMessage(), e);
        }
    }

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
        if (updates.getLogoUrl() != null) {
            tenant.setLogoUrl(updates.getLogoUrl());
        }

        tenant.setUpdatedAt(Instant.now());
        tenant = tenantRepository.save(tenant);

        log.info("Tenant updated successfully: {}", id);
        return tenant;
    }

    @Transactional
    public Tenant activateTenant(UUID id) {
        log.info("Activating tenant: {}", id);

        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new TenantNotFoundException("Tenant not found: " + id));

        if (!tenant.getStatus().canTransitionTo(TenantStatus.ACTIVE)) {
            throw new IllegalStateException("Cannot activate tenant in status: " + tenant.getStatus());
        }

        tenant.setStatus(TenantStatus.ACTIVE);
        tenant.setActivatedAt(Instant.now());
        tenant = tenantRepository.save(tenant);

        // Send notification
        notificationClient.sendNotification(
                tenant.getId(),
                NotificationServiceClient.NotificationType.TENANT_ACTIVATED,
                Map.of("tenantName", tenant.getName())
        );

        log.info("Tenant activated: {}", id);
        return tenant;
    }

    @Transactional
    public Tenant suspendTenant(UUID id, String reason) {
        log.warn("Suspending tenant: {} - Reason: {}", id, reason);

        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new TenantNotFoundException("Tenant not found: " + id));

        if (!tenant.getStatus().canTransitionTo(TenantStatus.SUSPENDED)) {
            throw new IllegalStateException("Cannot suspend tenant in status: " + tenant.getStatus());
        }

        tenant.setStatus(TenantStatus.SUSPENDED);
        tenant.setSuspendedAt(Instant.now());
        tenant.setSuspensionReason(reason);
        tenant = tenantRepository.save(tenant);

        // Send notification
        notificationClient.sendNotification(
                tenant.getId(),
                NotificationServiceClient.NotificationType.TENANT_SUSPENDED,
                Map.of(
                        "tenantName", tenant.getName(),
                        "reason", reason
                )
        );

        log.warn("Tenant suspended: {}", id);
        return tenant;
    }

    @Transactional
    public Tenant verifyTenant(UUID id, String verifiedBy, String verificationDocument) {
        log.info("Verifying tenant: {} by {}", id, verifiedBy);

        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new TenantNotFoundException("Tenant not found: " + id));

        tenant.setStatus(TenantStatus.ACTIVE);
        tenant.setVerifiedAt(Instant.now());
        tenant.setVerifiedBy(verifiedBy);

        if (verificationDocument != null) {
            Map<String, Object> metadata = tenant.getMetadata();
            if (metadata == null) {
                metadata = new HashMap<>();
                tenant.setMetadata(metadata);
            }
            metadata.put("verificationDocument", verificationDocument);
        }

        tenant = tenantRepository.save(tenant);

        log.info("Tenant verified: {}", id);
        return tenant;
    }

    @Transactional
    public void markForDeletion(UUID id) {
        log.warn("Marking tenant for deletion: {}", id);

        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new TenantNotFoundException("Tenant not found: " + id));

        if (!tenant.getStatus().canTransitionTo(TenantStatus.MARKED_FOR_DELETION)) {
            throw new IllegalStateException("Cannot delete tenant in status: " + tenant.getStatus());
        }

        tenant.setStatus(TenantStatus.MARKED_FOR_DELETION);
        tenant.setMarkedForDeletionAt(Instant.now());
        tenantRepository.save(tenant);

        log.warn("Tenant marked for deletion: {}", id);
    }


    public Optional<Tenant> getTenantById(UUID id) {
        log.debug("Fetching tenant by ID: {}", id);
        return tenantRepository.findById(id);
    }

    public Optional<Tenant> getTenantByCode(String code) {
        log.debug("Fetching tenant by code: {}", code);
        return tenantRepository.findByTenantCode(code);
    }

    public Tenant findByCodeOrId(String identifier) {
        log.debug("Finding tenant by code or ID: {}", identifier);

        // Try as UUID first
        try {
            UUID id = UUID.fromString(identifier);
            return tenantRepository.findById(id).orElse(null);
        } catch (IllegalArgumentException e) {
            // Not a UUID, try as code
            return tenantRepository.findByTenantCode(identifier).orElse(null);
        }
    }

    public Page<Tenant> searchTenants(String search, Pageable pageable) {
        log.debug("Searching tenants with query: {}", search);

        if (search == null || search.trim().isEmpty()) {
            return tenantRepository.findAll(pageable);
        }

        return tenantRepository.searchTenants(search, pageable);
    }

    public List<Tenant> getActiveTenants() {
        return tenantRepository.findByStatusIn(
                List.of(TenantStatus.ACTIVE, TenantStatus.TRIAL)
        );
    }

    public List<Tenant> getTenantsByOrganizationType(OrganizationType type) {
        return tenantRepository.findByOrganizationType(type);
    }

    public List<Tenant> getExpiringTenants(int daysAhead) {
        Instant expiryDate = Instant.now().plusSeconds(daysAhead * 86400L);
        return tenantRepository.findExpiringTenants(expiryDate);
    }

    public List<Tenant> getTrialsEndingSoon(int daysAhead) {
        Instant endDate = Instant.now().plusSeconds(daysAhead * 86400L);
        return tenantRepository.findTrialsEndingSoon(endDate);
    }

    public TenantStatistics getTenantStatistics() {
        long totalTenants = tenantRepository.count();
        long activeTenants = tenantRepository.countByStatusIn(
                List.of(TenantStatus.ACTIVE, TenantStatus.TRIAL)
        );

        Map<OrganizationType, Long> statisticsByType = new HashMap<>();
        for (OrganizationType type : OrganizationType.values()) {
            long count = tenantRepository.countByOrganizationType(type);
            statisticsByType.put(type, count);
        }

        return new TenantStatistics(totalTenants, activeTenants, statisticsByType);
    }

    private String generateTenantCode(Tenant tenant) {
        String prefix = switch (tenant.getOrganizationType()) {
            case GOVERNMENT_AGENCY -> "GOV";
            case CORPORATION -> "CORP";
            case ACADEMIC_INSTITUTION -> "ACAD";
            case HEALTHCARE -> "HEALTH";
            case FINANCIAL_INSTITUTION -> "FIN";
            case NON_PROFIT -> "NPO";
            case STARTUP -> "START";
            case RESEARCH_ORGANIZATION -> "RES";
            case INDIVIDUAL -> "IND";
        };

        String timestamp = String.valueOf(System.currentTimeMillis()).substring(8);
        return prefix + "-" + timestamp;
    }

    public static class TenantNotFoundException extends RuntimeException {
        public TenantNotFoundException(String message) {
            super(message);
        }
    }

    public static class TenantStatistics {
        private final long totalTenants;
        private final long activeTenants;
        private final Map<OrganizationType, Long> statisticsByType;

        public TenantStatistics(long totalTenants, long activeTenants,
                                Map<OrganizationType, Long> statisticsByType) {
            this.totalTenants = totalTenants;
            this.activeTenants = activeTenants;
            this.statisticsByType = statisticsByType;
        }

        public long getTotalTenants() { return totalTenants; }
        public long getActiveTenants() { return activeTenants; }
        public Map<OrganizationType, Long> getStatisticsByType() { return statisticsByType; }
    }

    /**
     * Validates tenant creation request.
     */
    private void validateTenantCreation(Tenant tenant) {
        // Check for duplicate tenant code
        if (tenant.getTenantCode() != null) {
            if (tenantRepository.existsByTenantCode(tenant.getTenantCode())) {
                throw new DuplicateTenantException("Tenant code already exists: " + tenant.getTenantCode());
            }
        }

        // Check for duplicate organization email
        if (tenant.getOrganizationEmail() != null) {
            if (tenantRepository.existsByOrganizationEmail(tenant.getOrganizationEmail())) {
                throw new DuplicateTenantException("Organization email already registered: " +
                        tenant.getOrganizationEmail());
            }
        }

        // Validate organization type specific requirements
        validateOrganizationRequirements(tenant);
    }

    /**
     * Validates organization-specific requirements.
     */
    private void validateOrganizationRequirements(Tenant tenant) {
        OrganizationType orgType = tenant.getOrganizationType();

        if (orgType == OrganizationType.GOVERNMENT_AGENCY) {
            if (tenant.getTaxId() == null || tenant.getTaxId().isEmpty()) {
                throw new ValidationException("Government agencies must provide a tax ID");
            }
        }

        if (orgType == OrganizationType.HEALTHCARE) {
            if (!tenant.getComplianceFrameworks().contains(ComplianceFramework.HIPAA)) {
                log.warn("Healthcare organization without HIPAA compliance: {}", tenant.getName());
            }
        }

        if (orgType == OrganizationType.FINANCIAL_INSTITUTION) {
            if (tenant.getBusinessLicense() == null || tenant.getBusinessLicense().isEmpty()) {
                throw new ValidationException("Financial institutions must provide a business license");
            }
        }
    }

    /**
     * Sends tenant created notification.
     */
    private void sendTenantCreatedNotification(Tenant tenant) {
        try {
            notificationClient.sendNotification(
                    tenant.getId(),
                    NotificationServiceClient.NotificationType.TENANT_ACTIVATED,
                    Map.of(
                            "tenantName", tenant.getName(),
                            "organizationType", tenant.getOrganizationType().toString(),
                            "isolationStrategy", tenant.getIsolationStrategy().toString(),
                            "status", tenant.getStatus().toString()
                    )
            );
        } catch (Exception e) {
            log.error("Failed to send tenant created notification", e);
            // Don't fail the creation if notification fails
        }
    }

    /**
     * Rollback tenant creation in case of failure.
     */
    private void rollbackTenantCreation(Tenant tenant) {
        try {
            log.warn("Rolling back tenant creation for: {}", tenant.getName());

            // Deprovision resources
            if (tenant.getIsolationStrategy() != null) {
                provisioningService.deprovisionTenant(tenant);
            }

            // Mark tenant as failed
            tenant.setStatus(TenantStatus.CREATION_FAILED);
            tenant.setIsDeleted(true);
            tenant.setDeletedAt(Instant.now());
            tenantRepository.save(tenant);

        } catch (Exception e) {
            log.error("Failed to rollback tenant creation", e);
        }
    }

    /**
     * Gets tenant provisioning status.
     */
    public TenantProvisioningService.ProvisioningStatus getProvisioningStatus(UUID tenantId) {
        return provisioningService.getProvisioningStatus(tenantId);
    }

    /**
     * Retries failed provisioning.
     */
    @Transactional
    public Tenant retryProvisioning(UUID tenantId) {
        log.info("Retrying provisioning for tenant: {}", tenantId);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException("Tenant not found: " + tenantId));

        if (tenant.getStatus() != TenantStatus.PROVISIONING_FAILED &&
                tenant.getStatus() != TenantStatus.CREATION_FAILED) {
            throw new IllegalStateException("Tenant is not in failed state: " + tenant.getStatus());
        }

        // Reset status and retry
        tenant.setStatus(TenantStatus.PENDING_VERIFICATION);
        tenant = provisioningService.provisionTenant(tenant);

        log.info("Provisioning retry completed for tenant: {}", tenant.getName());
        return tenant;
    }

    /**
     * Custom exceptions.
     */
    public static class DuplicateTenantException extends RuntimeException {
        public DuplicateTenantException(String message) {
            super(message);
        }
    }

    public static class TenantCreationException extends RuntimeException {
        public TenantCreationException(String message) {
            super(message);
        }

        public TenantCreationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class ValidationException extends RuntimeException {
        public ValidationException(String message) {
            super(message);
        }
    }
}