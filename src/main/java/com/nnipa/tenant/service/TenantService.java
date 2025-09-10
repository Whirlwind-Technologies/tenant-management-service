package com.nnipa.tenant.service;

import com.nnipa.tenant.client.NotificationServiceClient;
import com.nnipa.tenant.entity.Tenant;
import com.nnipa.tenant.entity.TenantSettings;
import com.nnipa.tenant.enums.ComplianceFramework;
import com.nnipa.tenant.enums.IsolationStrategy;
import com.nnipa.tenant.enums.OrganizationType;
import com.nnipa.tenant.enums.TenantStatus;
import com.nnipa.tenant.event.publisher.TenantEventPublisher;
import com.nnipa.tenant.exception.*;
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

/**
 * Service for managing tenant lifecycle.
 * Focuses on: configuration, metadata, billing, and feature flags.
 * Storage operations are handled by data-storage-service via events.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;
    private final OrganizationClassificationService classificationService;
    private final TenantSettingsService settingsService;
    private final NotificationServiceClient notificationClient;
    private final TenantProvisioningService provisioningService;
    private final TenantEventPublisher eventPublisher; // NEW: Kafka event publisher
    private final BillingService billingService; // NEW: Billing service
    private final FeatureFlagService featureFlagService; // NEW: Feature flag service

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

            // Step 6: Initialize subscription plan and billing
            if (tenant.getSubscriptionPlan() == null) {
                tenant.setSubscriptionPlan(determineDefaultPlan(tenant.getOrganizationType()));
            }

            // Step 7: Save tenant to database first
            tenant = tenantRepository.save(tenant);

            // Step 8: Initialize billing
            billingService.initializeBilling(tenant.getId(), tenant.getSubscriptionPlan());

            // Step 9: Initialize feature flags
            Map<String, Boolean> featureFlags = featureFlagService.initializeFeatureFlags(
                    tenant.getId(), tenant.getSubscriptionPlan());
            tenant.setFeatureFlags(featureFlags);

            // Step 10: Provision the tenant (basic setup only - no storage)
            // Storage provisioning will be handled by data-storage-service via event
            tenant = provisioningService.provisionTenant(tenant);

            // Step 11: Create default settings
            TenantSettings settings = settingsService.createDefaultSettings(tenant);
            tenant.setSettings(settings);

            // Step 12: Update with all configurations
            tenant = tenantRepository.save(tenant);

            // Step 13: Publish tenant created event (for other services)
            eventPublisher.publishTenantCreatedEvent(tenant);

            // Step 14: Send notification asynchronously
            sendTenantCreatedNotification(tenant);

            log.info("Tenant created successfully: {} (ID: {}, Status: {})",
                    tenant.getName(), tenant.getId(), tenant.getStatus());

            return tenant;

        } catch (DuplicateTenantException | TenantValidationException | ValidationException e) {
            log.error("Tenant validation failed: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Failed to create tenant: {}", tenant.getName(), e);

            if (tenant.getId() != null) {
                rollbackTenantCreation(tenant);
            }

            if (e instanceof TenantException) {
                throw (TenantException) e;
            }
            throw new TenantCreationException("Failed to create tenant: " + e.getMessage(), e);
        }
    }

    @Transactional
    @CacheEvict(value = "tenants", key = "#id")
    public Tenant updateTenant(UUID id, Tenant updates) {
        log.info("Updating tenant: {}", id);

        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new TenantNotFoundException("Tenant not found with ID: " + id));

        // Track if subscription changed
        String oldSubscriptionPlan = tenant.getSubscriptionPlan();

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
            if (!tenant.getOrganizationEmail().equals(updates.getOrganizationEmail())) {
                if (tenantRepository.existsByOrganizationEmail(updates.getOrganizationEmail())) {
                    throw new DuplicateTenantException("Organization email already registered: " +
                            updates.getOrganizationEmail());
                }
            }
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

        // Handle subscription plan changes
        if (updates.getSubscriptionPlan() != null &&
                !updates.getSubscriptionPlan().equals(oldSubscriptionPlan)) {
            tenant.setSubscriptionPlan(updates.getSubscriptionPlan());

            // Update billing
            billingService.updateSubscription(tenant.getId(), updates.getSubscriptionPlan());

            // Update feature flags
            Map<String, Boolean> newFlags = featureFlagService.updateFeatureFlagsForPlan(
                    tenant.getId(), updates.getSubscriptionPlan());
            tenant.setFeatureFlags(newFlags);

            // Publish subscription changed event
            eventPublisher.publishSubscriptionChangedEvent(tenant, oldSubscriptionPlan,
                    updates.getSubscriptionPlan());
        }

        tenant.setUpdatedAt(Instant.now());
        tenant = tenantRepository.save(tenant);

        // Publish tenant updated event
        eventPublisher.publishTenantUpdatedEvent(tenant);

        log.info("Tenant updated successfully: {}", id);
        return tenant;
    }

    @Transactional
    public Tenant activateTenant(UUID id) {
        log.info("Activating tenant: {}", id);

        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new TenantNotFoundException("Tenant not found with ID: " + id));

        if (!tenant.getStatus().canTransitionTo(TenantStatus.ACTIVE)) {
            throw new InvalidTenantStatusException(
                    tenant.getStatus().toString(),
                    TenantStatus.ACTIVE.toString()
            );
        }

        tenant.setStatus(TenantStatus.ACTIVE);
        tenant.setActivatedAt(Instant.now());
        tenant = tenantRepository.save(tenant);

        log.info("Tenant activated successfully: {}", id);
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

        // Publish tenant suspended event
        eventPublisher.publishTenantSuspendedEvent(tenant, reason);

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
    public Tenant reactivateTenant(UUID id, String reactivatedBy) {
        log.info("Reactivating tenant: {}", id);

        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new TenantNotFoundException("Tenant not found: " + id));

        if (tenant.getStatus() != TenantStatus.SUSPENDED &&
                tenant.getStatus() != TenantStatus.DEACTIVATED) {
            throw new IllegalStateException("Tenant is not suspended or deactivated");
        }

        tenant.setStatus(TenantStatus.ACTIVE);
        tenant.setSuspendedAt(null);
        tenant.setSuspensionReason(null);
        tenant.setUpdatedAt(Instant.now());
        tenant = tenantRepository.save(tenant);

        // Publish reactivation event
        eventPublisher.publishTenantReactivatedEvent(tenant, reactivatedBy);

        log.info("Reactivated tenant: {}", id);
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

    @Transactional
    @CacheEvict(value = "tenants", key = "#id")
    public void deleteTenant(UUID id) {
        log.info("Deleting tenant: {}", id);

        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new TenantNotFoundException("Tenant not found with ID: " + id));

        if (tenant.getStatus() == TenantStatus.ACTIVE) {
            throw new InvalidTenantStatusException(
                    tenant.getStatus().toString(),
                    "DELETED"
            );
        }

        // Soft delete
        tenant.setIsDeleted(true);
        tenant.setDeletedAt(Instant.now());
        tenant.setStatus(TenantStatus.DELETED);
        tenantRepository.save(tenant);

        // Cancel billing
        billingService.cancelSubscription(tenant.getId());

        // Deprovision basic resources (not storage)
        provisioningService.deprovisionTenant(tenant);

        // Publish tenant deactivated event
        // Data-storage-service will handle storage cleanup
        eventPublisher.publishTenantDeactivatedEvent(tenant);

        log.info("Tenant deleted successfully: {}", id);
    }


    /**
     * Migrate tenant to different isolation strategy.
     */
    @Transactional
    public Tenant migrateTenant(UUID tenantId, String toStrategy, String migratedBy) {
        log.info("Migrating tenant: {} to strategy: {}", tenantId, toStrategy);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException("Tenant not found: " + tenantId));

        String fromStrategy = tenant.getIsolationStrategy().toString();

        if (fromStrategy.equals(toStrategy)) {
            log.info("Isolation strategy unchanged for tenant: {}", tenantId);
            return tenant;
        }

        // Update strategy
        tenant.setIsolationStrategy(parseIsolationStrategy(toStrategy));
        tenant.setStatus(TenantStatus.MIGRATING);
        tenant.setUpdatedAt(Instant.now());
        tenant = tenantRepository.save(tenant);

        // Publish migration event
        // Data-storage-service and other services will handle the actual migration
        eventPublisher.publishTenantMigratedEvent(tenant, fromStrategy, toStrategy);

        log.info("Initiated migration for tenant: {} from {} to {}",
                tenantId, fromStrategy, toStrategy);

        return tenant;
    }

    /**
     * Update tenant user count (called by user-management-service via events).
     */
    @Transactional
    public void incrementUserCount(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException("Tenant not found: " + tenantId));

        Integer currentUsers = tenant.getCurrentUsers() != null ? tenant.getCurrentUsers() : 0;
        tenant.setCurrentUsers(currentUsers + 1);

        // Check if user limit exceeded
        if (tenant.getMaxUsers() != null && tenant.getCurrentUsers() > tenant.getMaxUsers()) {
            log.warn("Tenant {} has exceeded user limit: {}/{}",
                    tenantId, tenant.getCurrentUsers(), tenant.getMaxUsers());

            // Could send notification or take action
            eventPublisher.publishUserLimitExceededEvent(tenant);
        }

        tenantRepository.save(tenant);
        log.debug("Incremented user count for tenant {} to {}", tenantId, tenant.getCurrentUsers());
    }

    @Transactional
    public void decrementUserCount(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException("Tenant not found: " + tenantId));

        Integer currentUsers = tenant.getCurrentUsers() != null ? tenant.getCurrentUsers() : 0;
        if (currentUsers > 0) {
            tenant.setCurrentUsers(currentUsers - 1);
            tenantRepository.save(tenant);
            log.debug("Decremented user count for tenant {} to {}", tenantId, tenant.getCurrentUsers());
        }
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

        // Try to parse as UUID first
        try {
            UUID id = UUID.fromString(identifier);
            return tenantRepository.findById(id)
                    .orElseThrow(() -> new TenantNotFoundException("Tenant not found with ID: " + identifier));
        } catch (IllegalArgumentException e) {
            // Not a UUID, try as tenant code
            return tenantRepository.findByTenantCode(identifier)
                    .orElseThrow(() -> new TenantNotFoundException("Tenant not found with code: " + identifier));
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

        // Validate required fields
        if (tenant.getName() == null || tenant.getName().trim().isEmpty()) {
            throw new TenantValidationException("Tenant name is required");
        }

        if (tenant.getOrganizationEmail() == null || tenant.getOrganizationEmail().trim().isEmpty()) {
            throw new TenantValidationException("Organization email is required");
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
                .orElseThrow(() -> new TenantNotFoundException("Tenant not found with ID: " + tenantId));

        if (tenant.getStatus() != TenantStatus.PROVISIONING_FAILED &&
                tenant.getStatus() != TenantStatus.CREATION_FAILED) {
            throw new InvalidTenantStatusException(
                    tenant.getStatus().toString(),
                    TenantStatus.PENDING_VERIFICATION.toString()
            );
        }

        // Reset status and retry
        tenant.setStatus(TenantStatus.PENDING_VERIFICATION);
        tenant = provisioningService.provisionTenant(tenant);

        log.info("Provisioning retry completed for tenant: {}", tenant.getName());
        return tenant;
    }

    private String determineDefaultPlan(OrganizationType orgType) {
        return switch (orgType) {
            case GOVERNMENT_AGENCY, CORPORATION -> "ENTERPRISE";
            case FINANCIAL_INSTITUTION, HEALTHCARE -> "PROFESSIONAL";
            case ACADEMIC_INSTITUTION, NON_PROFIT -> "STANDARD";
            case STARTUP, RESEARCH_ORGANIZATION, INDIVIDUAL -> "TRIAL";
        };
    }

    private IsolationStrategy parseIsolationStrategy(String strategy) {
        try {
            return IsolationStrategy.valueOf(strategy);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Invalid isolation strategy: " + strategy);
        }
    }
}