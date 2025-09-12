package com.nnipa.tenant.service;

import com.nnipa.tenant.dto.request.CreateTenantRequest;
import com.nnipa.tenant.dto.request.UpdateTenantRequest;
import com.nnipa.tenant.dto.response.TenantResponse;
import com.nnipa.tenant.dto.response.TenantSummaryResponse;
import com.nnipa.tenant.entity.Tenant;
import com.nnipa.tenant.enums.OrganizationType;
import com.nnipa.tenant.enums.TenantStatus;
import com.nnipa.tenant.event.publisher.TenantEventPublisher;
import com.nnipa.tenant.exception.TenantAlreadyExistsException;
import com.nnipa.tenant.exception.TenantNotFoundException;
import com.nnipa.tenant.mapper.TenantMapper;
import com.nnipa.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service class for managing tenants
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;
    private final TenantMapper tenantMapper;
    private final SubscriptionService subscriptionService;
    private final FeatureFlagService featureFlagService;
    private final TenantSettingsService settingsService;
    private final TenantEventPublisher eventPublisher;

    /**
     * Create a new tenant
     */
    @Transactional
    public TenantResponse createTenant(CreateTenantRequest request, String createdBy) {
        log.info("Creating tenant with code: {}", request.getTenantCode());

        // Check if tenant code already exists
        if (tenantRepository.existsByTenantCode(request.getTenantCode())) {
            throw new TenantAlreadyExistsException("Tenant with code " + request.getTenantCode() + " already exists");
        }

        // Create tenant entity
        Tenant tenant = tenantMapper.toEntity(request);
        tenant.setCreatedBy(createdBy);
        tenant.setStatus(TenantStatus.PENDING);

        // Handle parent tenant if specified
        if (request.getParentTenantCode() != null) {
            Tenant parentTenant = tenantRepository.findByTenantCode(request.getParentTenantCode())
                    .orElseThrow(() -> new TenantNotFoundException("Parent tenant not found: " + request.getParentTenantCode()));
            tenant.setParentTenant(parentTenant);
        }

        // Set trial end date if trial plan
        if (request.getSubscriptionPlan().name().equals("TRIAL")) {
            tenant.setStatus(TenantStatus.TRIAL);
            tenant.setTrialEndsAt(request.getTrialEndsAt() != null ?
                    request.getTrialEndsAt() : LocalDateTime.now().plusDays(30));
        }

        // Save tenant
        tenant = tenantRepository.save(tenant);
        log.info("Tenant created with ID: {}", tenant.getId());

        // Create subscription
        subscriptionService.createSubscription(tenant, request);

        // Create default settings
        settingsService.createDefaultSettings(tenant, request.getInitialSettings());

        // Enable initial feature flags
        if (request.getInitialFeatureFlags() != null) {
            featureFlagService.initializeFeatureFlags(tenant, request.getInitialFeatureFlags());
        }

        // Auto-activate if requested
        if (Boolean.TRUE.equals(request.getAutoActivate())) {
            tenant = activateTenant(tenant.getId(), createdBy);
        }

        // Publish event
        eventPublisher.publishTenantCreatedEvent(tenant);

        return tenantMapper.toResponse(tenant);
    }

    /**
     * Update tenant information
     */
    @Transactional
    @CacheEvict(value = "tenants", key = "#tenantId")
    public TenantResponse updateTenant(UUID tenantId, UpdateTenantRequest request, String updatedBy) {
        log.info("Updating tenant: {}", tenantId);

        Tenant tenant = findTenantById(tenantId);

        // Update fields
        tenantMapper.updateEntity(tenant, request);
        tenant.setUpdatedBy(updatedBy);

        tenant = tenantRepository.save(tenant);

        // Publish event
        eventPublisher.publishTenantUpdatedEvent(tenant);

        return tenantMapper.toResponse(tenant);
    }

    /**
     * Get tenant by ID
     */
    @Cacheable(value = "tenants", key = "#tenantId")
    public TenantResponse getTenant(UUID tenantId) {
        log.debug("Fetching tenant: {}", tenantId);
        Tenant tenant = findTenantById(tenantId);
        return tenantMapper.toResponse(tenant);
    }

    /**
     * Get tenant by code
     */
    @Cacheable(value = "tenants", key = "#tenantCode")
    public TenantResponse getTenantByCode(String tenantCode) {
        log.debug("Fetching tenant by code: {}", tenantCode);
        Tenant tenant = tenantRepository.findByTenantCodeAndIsDeletedFalse(tenantCode)
                .orElseThrow(() -> new TenantNotFoundException("Tenant not found: " + tenantCode));
        return tenantMapper.toResponse(tenant);
    }

    /**
     * List all tenants with pagination
     */
    public Page<TenantSummaryResponse> listTenants(Pageable pageable) {
        log.debug("Listing tenants, page: {}, size: {}", pageable.getPageNumber(), pageable.getPageSize());
        return tenantRepository.findAll(pageable)
                .map(tenantMapper::toSummaryResponse);
    }

    /**
     * List tenants by status
     */
    public Page<TenantSummaryResponse> listTenantsByStatus(TenantStatus status, Pageable pageable) {
        log.debug("Listing tenants by status: {}", status);
        return tenantRepository.findByStatus(status, pageable)
                .map(tenantMapper::toSummaryResponse);
    }

    /**
     * List tenants by organization type
     */
    public Page<TenantSummaryResponse> listTenantsByType(OrganizationType type, Pageable pageable) {
        log.debug("Listing tenants by type: {}", type);
        return tenantRepository.findByOrganizationType(type, pageable)
                .map(tenantMapper::toSummaryResponse);
    }

    /**
     * Activate a tenant
     */
    @Transactional
    @CacheEvict(value = "tenants", key = "#tenantId")
    public Tenant activateTenant(UUID tenantId, String activatedBy) {
        log.info("Activating tenant: {}", tenantId);

        Tenant tenant = findTenantById(tenantId);

        if (tenant.isActive()) {
            log.warn("Tenant {} is already active", tenantId);
            return tenant;
        }

        tenant.activate();
        tenant.setUpdatedBy(activatedBy);
        tenant = tenantRepository.save(tenant);

        // Activate subscription
        subscriptionService.activateSubscription(tenant.getId());

        // Publish event
        eventPublisher.publishTenantActivatedEvent(tenant, activatedBy);

        log.info("Tenant {} activated successfully", tenantId);
        return tenant;
    }

    /**
     * Suspend a tenant
     */
    @Transactional
    @CacheEvict(value = "tenants", key = "#tenantId")
    public TenantResponse suspendTenant(UUID tenantId, String reason, String suspendedBy) {
        log.info("Suspending tenant: {}, reason: {}", tenantId, reason);

        Tenant tenant = findTenantById(tenantId);
        tenant.suspend(reason);
        tenant.setUpdatedBy(suspendedBy);
        tenant = tenantRepository.save(tenant);

        // Suspend subscription
        subscriptionService.pauseSubscription(tenant.getId());

        // Publish event
        eventPublisher.publishTenantSuspendedEvent(tenant, reason);

        return tenantMapper.toResponse(tenant);
    }

    /**
     * Reactivate a suspended tenant
     */
    @Transactional
    @CacheEvict(value = "tenants", key = "#tenantId")
    public TenantResponse reactivateTenant(UUID tenantId, String reactivatedBy) {
        log.info("Reactivating tenant: {}", tenantId);

        Tenant tenant = findTenantById(tenantId);

        if (!TenantStatus.SUSPENDED.equals(tenant.getStatus())) {
            throw new IllegalStateException("Tenant is not suspended");
        }

        tenant.reactivate();
        tenant.setUpdatedBy(reactivatedBy);
        tenant = tenantRepository.save(tenant);

        // Resume subscription
        subscriptionService.resumeSubscription(tenant.getId());

        // Publish event
        eventPublisher.publishTenantReactivatedEvent(tenant, reactivatedBy);

        return tenantMapper.toResponse(tenant);
    }

    /**
     * Delete a tenant (soft delete)
     */
    @Transactional
    @CacheEvict(value = "tenants", key = "#tenantId")
    public void deleteTenant(UUID tenantId, String deletedBy) {
        log.info("Deleting tenant: {}", tenantId);

        Tenant tenant = findTenantById(tenantId);
        tenant.markAsDeleted(deletedBy);
        tenant.setStatus(TenantStatus.DELETED);
        tenantRepository.save(tenant);

        // Cancel subscription
        subscriptionService.cancelSubscription(tenant.getId(), "Tenant deleted");

        // Publish event
        eventPublisher.publishTenantDeletedEvent(tenant, deletedBy);

        log.info("Tenant {} marked as deleted", tenantId);
    }

    /**
     * Process expiring trials
     */
    @Transactional
    public void processExpiringTrials() {
        log.info("Processing expiring trials");

        LocalDateTime tomorrow = LocalDateTime.now().plusDays(1);
        List<Tenant> expiringTrials = tenantRepository.findExpiringTrials(tomorrow);

        for (Tenant tenant : expiringTrials) {
            log.info("Trial expiring for tenant: {}", tenant.getTenantCode());

            // Send notification
//            eventPublisher.publishTrialExpiringEvent(tenant);

            // Auto-convert to paid plan if configured
            if (tenant.getSubscription() != null && tenant.getSubscription().getAutoRenew()) {
                subscriptionService.convertTrialToPaid(tenant.getId());
            }
        }

        log.info("Processed {} expiring trials", expiringTrials.size());
    }

    /**
     * Get tenant statistics
     */
    public Map<String, Object> getTenantStatistics() {
        Map<String, Object> stats = new HashMap<>();

        stats.put("totalTenants", tenantRepository.count());
        stats.put("activeTenants", tenantRepository.countByStatus(TenantStatus.ACTIVE));
        stats.put("trialTenants", tenantRepository.countByStatus(TenantStatus.TRIAL));
        stats.put("suspendedTenants", tenantRepository.countByStatus(TenantStatus.SUSPENDED));

        return stats;
    }

    /**
     * Helper method to find tenant by ID
     */
    private Tenant findTenantById(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException("Tenant not found: " + tenantId));
    }
}