package com.nnipa.tenant.service;

import com.nnipa.tenant.entity.Tenant;
import com.nnipa.tenant.entity.TenantSettings;
import com.nnipa.tenant.enums.OrganizationType;
import com.nnipa.tenant.repository.TenantSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing tenant settings and preferences.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantSettingsService {

    private final TenantSettingsRepository settingsRepository;

    /**
     * Creates default settings for a new tenant based on organization type.
     */
    @Transactional
    public TenantSettings createDefaultSettings(Tenant tenant) {
        log.info("Creating default settings for tenant: {}", tenant.getName());

        TenantSettings settings = TenantSettings.builder()
                .tenant(tenant)
                .defaultLanguage("en")
                .dateFormat("yyyy-MM-dd")
                .timeFormat("HH:mm:ss")
                .numberFormat("#,##0.00")
                .currencyFormat("$#,##0.00")
                .timezone(tenant.getTimezone() != null ? tenant.getTimezone() : "UTC")
                .fiscalYearStart("01-01")
                .build();

        // Set organization-specific defaults
        configureOrganizationDefaults(settings, tenant.getOrganizationType());

        // Configure data management settings
        configureDataSettings(settings, tenant.getOrganizationType());

        // Configure UI settings
        configureUISettings(settings, tenant);

        // Configure business settings
        configureBusinessSettings(settings, tenant.getOrganizationType());

        // Initialize custom settings
        settings.setCustomSettings(new HashMap<>());
        settings.setComplianceSettings(new HashMap<>());
        settings.setWorkflowSettings(new HashMap<>());
        settings.setIntegrationSettings(new HashMap<>());
        settings.setNotificationPreferences(getDefaultNotificationPreferences());

        settings = settingsRepository.save(settings);
        log.info("Default settings created for tenant: {}", tenant.getName());

        return settings;
    }

    /**
     * Updates tenant settings.
     */
    @Transactional
    public TenantSettings updateSettings(UUID tenantId, TenantSettings updates) {
        log.info("Updating settings for tenant: {}", tenantId);

        TenantSettings settings = settingsRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new RuntimeException("Settings not found for tenant: " + tenantId));

        // Update allowed fields
        if (updates.getDefaultLanguage() != null) {
            settings.setDefaultLanguage(updates.getDefaultLanguage());
        }
        if (updates.getDateFormat() != null) {
            settings.setDateFormat(updates.getDateFormat());
        }
        if (updates.getTimeFormat() != null) {
            settings.setTimeFormat(updates.getTimeFormat());
        }
        if (updates.getTimezone() != null) {
            settings.setTimezone(updates.getTimezone());
        }
        if (updates.getTheme() != null) {
            settings.setTheme(updates.getTheme());
        }
        if (updates.getDataRetentionDays() != null) {
            settings.setDataRetentionDays(updates.getDataRetentionDays());
        }
        if (updates.getBackupFrequency() != null) {
            settings.setBackupFrequency(updates.getBackupFrequency());
        }
        if (updates.getMaxExportRows() != null) {
            settings.setMaxExportRows(updates.getMaxExportRows());
        }

        return settingsRepository.save(settings);
    }

    /**
     * Gets settings for a tenant.
     */
    public Optional<TenantSettings> getSettingsByTenantId(UUID tenantId) {
        return settingsRepository.findByTenantId(tenantId);
    }

    private void configureOrganizationDefaults(TenantSettings settings, OrganizationType type) {
        // Data retention based on organization type
        settings.setDataRetentionDays(switch (type) {
            case GOVERNMENT_AGENCY, FINANCIAL_INSTITUTION -> 2555; // 7 years
            case HEALTHCARE -> 2190; // 6 years
            case ACADEMIC_INSTITUTION -> 1095; // 3 years
            case CORPORATION, NON_PROFIT -> 730; // 2 years
            default -> 365; // 1 year
        });

        // Backup settings
        settings.setBackupFrequency(switch (type) {
            case GOVERNMENT_AGENCY, FINANCIAL_INSTITUTION, HEALTHCARE -> "HOURLY";
            case CORPORATION, ACADEMIC_INSTITUTION -> "DAILY";
            default -> "WEEKLY";
        });

        settings.setBackupRetentionDays(switch (type) {
            case GOVERNMENT_AGENCY, FINANCIAL_INSTITUTION -> 90;
            case HEALTHCARE, CORPORATION -> 60;
            default -> 30;
        });
    }

    private void configureDataSettings(TenantSettings settings, OrganizationType type) {
        settings.setAutoBackupEnabled(true);
        settings.setDataExportEnabled(true);
        settings.setAllowedExportFormats("CSV,JSON,EXCEL");

        settings.setMaxExportRows(switch (type) {
            case GOVERNMENT_AGENCY, CORPORATION -> 1000000;
            case FINANCIAL_INSTITUTION, ACADEMIC_INSTITUTION -> 500000;
            case HEALTHCARE, RESEARCH_ORGANIZATION -> 250000;
            default -> 100000;
        });

        // Set collaboration features
        settings.setEnableDataCollaboration(switch (type) {
            case GOVERNMENT_AGENCY, ACADEMIC_INSTITUTION, RESEARCH_ORGANIZATION -> true;
            default -> false;
        });

        settings.setEnablePublicDashboards(type == OrganizationType.ACADEMIC_INSTITUTION ||
                type == OrganizationType.NON_PROFIT);
    }

    private void configureUISettings(TenantSettings settings, Tenant tenant) {
        settings.setTheme("light");
        settings.setPrimaryColor(tenant.getPrimaryColor() != null ?
                tenant.getPrimaryColor() : "#1976D2");
        settings.setSecondaryColor(tenant.getSecondaryColor() != null ?
                tenant.getSecondaryColor() : "#424242");
        settings.setLogoUrl(tenant.getLogoUrl());
        settings.setShowLogo(true);
        settings.setEnableCustomReports(true);
        settings.setEnableAdvancedAnalytics(
                tenant.getOrganizationType() != OrganizationType.INDIVIDUAL
        );
        settings.setEnableApiAccess(true);
        settings.setEnableDashboardSharing(
                tenant.getOrganizationType() != OrganizationType.INDIVIDUAL
        );
    }

    private void configureBusinessSettings(TenantSettings settings, OrganizationType type) {
        // Default business hours
        String businessHours = """
            {
                "monday": {"start": "09:00", "end": "17:00"},
                "tuesday": {"start": "09:00", "end": "17:00"},
                "wednesday": {"start": "09:00", "end": "17:00"},
                "thursday": {"start": "09:00", "end": "17:00"},
                "friday": {"start": "09:00", "end": "17:00"},
                "saturday": "closed",
                "sunday": "closed"
            }
            """;
        settings.setBusinessHours(businessHours);

        settings.setWorkingDays("MON,TUE,WED,THU,FRI");

        // Holiday calendar based on region
        settings.setHolidayCalendar(switch (type) {
            case GOVERNMENT_AGENCY -> "US_FEDERAL";
            case ACADEMIC_INSTITUTION -> "ACADEMIC";
            default -> "STANDARD";
        });
    }

    private Map<String, Object> getDefaultNotificationPreferences() {
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("email", true);
        prefs.put("sms", false);
        prefs.put("webhook", false);
        prefs.put("inApp", true);
        prefs.put("digest", "DAILY");
        prefs.put("criticalAlerts", true);
        prefs.put("usageAlerts", true);
        prefs.put("systemUpdates", true);
        return prefs;
    }
}