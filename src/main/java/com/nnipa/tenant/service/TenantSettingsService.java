package com.nnipa.tenant.service;

import com.nnipa.tenant.dto.request.TenantSettingsRequest;
import com.nnipa.tenant.dto.response.TenantSettingsResponse;
import com.nnipa.tenant.entity.Tenant;
import com.nnipa.tenant.entity.TenantSettings;
import com.nnipa.tenant.exception.ResourceNotFoundException;
import com.nnipa.tenant.mapper.TenantSettingsMapper;
import com.nnipa.tenant.repository.TenantSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Service for managing tenant settings
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantSettingsService {

    private final TenantSettingsRepository settingsRepository;
    private final TenantSettingsMapper settingsMapper;

    /**
     * Create default settings for a new tenant
     */
    @Transactional
    public TenantSettings createDefaultSettings(Tenant tenant, Map<String, String> initialSettings) {
        log.info("Creating default settings for tenant: {}", tenant.getTenantCode());

        TenantSettings settings = TenantSettings.builder()
                .tenant(tenant)
                .timezone("UTC")
                .locale("en_US")
                .dateFormat("yyyy-MM-dd")
                .currency("USD")
                .enforceMfa(false)
                .sessionTimeoutMinutes(30)
                .sendBillingAlerts(true)
                .sendUsageAlerts(true)
                .sendSecurityAlerts(true)
                .auditLogEnabled(true)
                .dataRetentionDays(365)
                .customSettings(new HashMap<>())
                .webhookUrls(new HashMap<>())
                .apiKeys(new HashMap<>())
                .build();

        // Apply initial settings if provided
        if (initialSettings != null && !initialSettings.isEmpty()) {
            Map<String, Object> customSettings = new HashMap<>(initialSettings);
            settings.setCustomSettings(customSettings);
        }

        // Set organization-specific defaults
        setOrganizationDefaults(settings, tenant);

        settings = settingsRepository.save(settings);
        log.info("Created default settings for tenant: {}", tenant.getTenantCode());

        return settings;
    }

    /**
     * Get tenant settings
     */
    @Cacheable(value = "tenant-settings", key = "#tenantId")
    public TenantSettingsResponse getTenantSettings(UUID tenantId) {
        log.debug("Fetching settings for tenant: {}", tenantId);

        TenantSettings settings = settingsRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Settings not found for tenant: " + tenantId));

        return settingsMapper.toResponse(settings);
    }

    /**
     * Update tenant settings
     */
    @Transactional
    @CacheEvict(value = "tenant-settings", key = "#tenantId")
    public TenantSettingsResponse updateTenantSettings(UUID tenantId,
                                                       TenantSettingsRequest request,
                                                       String updatedBy) {
        log.info("Updating settings for tenant: {}", tenantId);

        TenantSettings settings = settingsRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Settings not found for tenant: " + tenantId));

        // Update fields
        settingsMapper.updateEntity(settings, request);
        settings.setUpdatedBy(updatedBy);

        settings = settingsRepository.save(settings);

        return settingsMapper.toResponse(settings);
    }

    /**
     * Set organization-specific defaults
     */
    private void setOrganizationDefaults(TenantSettings settings, Tenant tenant) {
        switch (tenant.getOrganizationType()) {
            case GOVERNMENT -> {
                settings.setEnforceMfa(true);
                settings.setPasswordExpiryDays(90);
                settings.setSessionTimeoutMinutes(15);
                settings.setComplianceFrameworks("FISMA,FedRAMP,NIST");
                settings.setDataRetentionDays(2555); // 7 years
            }
            case HEALTHCARE -> {
                settings.setEnforceMfa(true);
                settings.setPasswordExpiryDays(60);
                settings.setComplianceFrameworks("HIPAA");
                settings.setDataRetentionDays(2190); // 6 years
            }
            case FINANCIAL_INSTITUTION -> {
                settings.setEnforceMfa(true);
                settings.setPasswordExpiryDays(90);
                settings.setComplianceFrameworks("SOX,PCI-DSS");
                settings.setDataRetentionDays(2555); // 7 years
            }
            case ACADEMIC_INSTITUTION -> {
                settings.setComplianceFrameworks("FERPA");
                settings.setDataRetentionDays(1825); // 5 years
            }
            default -> {
                // Default settings already applied
            }
        }
    }
}