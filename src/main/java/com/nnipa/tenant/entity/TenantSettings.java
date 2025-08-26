package com.nnipa.tenant.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.HashMap;
import java.util.Map;

/**
 * Tenant settings entity storing configuration and preferences.
 * Authentication, authorization, rate limiting, and notifications are handled by separate services.
 */
@Entity
@Table(name = "tenant_settings", indexes = {
        @Index(name = "idx_tenant_settings_tenant", columnList = "tenant_id", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@ToString(exclude = "tenant")
@EqualsAndHashCode(callSuper = true, exclude = "tenant")
public class TenantSettings extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    // General Settings
    @Column(name = "default_language", length = 10)
    private String defaultLanguage = "en";

    @Column(name = "date_format", length = 50)
    private String dateFormat = "yyyy-MM-dd";

    @Column(name = "time_format", length = 50)
    private String timeFormat = "HH:mm:ss";

    @Column(name = "number_format", length = 50)
    private String numberFormat = "#,##0.00";

    @Column(name = "currency_format", length = 50)
    private String currencyFormat = "$#,##0.00";

    @Column(name = "timezone", length = 50)
    private String timezone = "UTC";

    @Column(name = "fiscal_year_start", length = 10)
    private String fiscalYearStart = "01-01"; // MM-DD format

    // Data Settings
    @Column(name = "data_retention_days")
    private Integer dataRetentionDays;

    @Column(name = "auto_backup_enabled", nullable = false)
    private Boolean autoBackupEnabled = true;

    @Column(name = "backup_frequency", length = 20)
    private String backupFrequency = "DAILY"; // HOURLY, DAILY, WEEKLY

    @Column(name = "backup_retention_days")
    private Integer backupRetentionDays = 30;

    @Column(name = "data_export_enabled", nullable = false)
    private Boolean dataExportEnabled = true;

    @Column(name = "allowed_export_formats", length = 255)
    private String allowedExportFormats = "CSV,JSON,EXCEL";

    @Column(name = "max_export_rows")
    private Integer maxExportRows = 100000;

    // UI Customization
    @Column(name = "theme", length = 50)
    private String theme = "light"; // light, dark, auto

    @Column(name = "primary_color", length = 7)
    private String primaryColor = "#1976D2";

    @Column(name = "secondary_color", length = 7)
    private String secondaryColor = "#424242";

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "favicon_url", length = 500)
    private String faviconUrl;

    @Column(name = "show_logo", nullable = false)
    private Boolean showLogo = true;

    @Column(name = "custom_css", columnDefinition = "TEXT")
    private String customCss;

    @Column(name = "dashboard_layout", columnDefinition = "TEXT")
    private String dashboardLayout; // JSON configuration

    @Column(name = "default_dashboard", length = 100)
    private String defaultDashboard;

    // Business Settings
    @Column(name = "business_hours", columnDefinition = "TEXT")
    private String businessHours; // JSON with day-wise hours

    @Column(name = "working_days", length = 50)
    private String workingDays = "MON,TUE,WED,THU,FRI";

    @Column(name = "holiday_calendar", length = 50)
    private String holidayCalendar; // Reference to holiday calendar

    // Feature Preferences (UI toggles, not access control)
    @Column(name = "enable_dashboard_sharing", nullable = false)
    private Boolean enableDashboardSharing = false;

    @Column(name = "enable_public_dashboards", nullable = false)
    private Boolean enablePublicDashboards = false;

    @Column(name = "enable_custom_reports", nullable = false)
    private Boolean enableCustomReports = true;

    @Column(name = "enable_data_collaboration", nullable = false)
    private Boolean enableDataCollaboration = false;

    @Column(name = "enable_advanced_analytics", nullable = false)
    private Boolean enableAdvancedAnalytics = false;

    @Column(name = "enable_api_access", nullable = false)
    private Boolean enableApiAccess = true;

    // Organization-specific Settings
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "compliance_settings", columnDefinition = "jsonb")
    private Map<String, Object> complianceSettings = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "workflow_settings", columnDefinition = "jsonb")
    private Map<String, Object> workflowSettings = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "integration_settings", columnDefinition = "jsonb")
    private Map<String, Object> integrationSettings = new HashMap<>();

    // Custom Settings (JSON)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "custom_settings", columnDefinition = "jsonb")
    private Map<String, Object> customSettings = new HashMap<>();

    // Notification Preferences (just preferences, not implementation)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "notification_preferences", columnDefinition = "jsonb")
    private Map<String, Object> notificationPreferences = new HashMap<>();

    // Helper Methods

    /**
     * Adds a custom setting.
     */
    public void addCustomSetting(String key, Object value) {
        if (customSettings == null) {
            customSettings = new HashMap<>();
        }
        customSettings.put(key, value);
    }

    /**
     * Gets a custom setting value.
     */
    @SuppressWarnings("unchecked")
    public <T> T getCustomSetting(String key, Class<T> type) {
        if (customSettings == null || !customSettings.containsKey(key)) {
            return null;
        }
        return (T) customSettings.get(key);
    }

    /**
     * Sets notification preference.
     */
    public void setNotificationPreference(String channel, boolean enabled) {
        if (notificationPreferences == null) {
            notificationPreferences = new HashMap<>();
        }
        notificationPreferences.put(channel, enabled);
    }

    /**
     * Gets notification preference.
     */
    public boolean getNotificationPreference(String channel) {
        if (notificationPreferences == null) {
            return true; // Default to enabled
        }
        return (boolean) notificationPreferences.getOrDefault(channel, true);
    }

    /**
     * Checks if a UI feature is enabled.
     * Note: This is for UI preferences only, not access control.
     */
    public boolean isUiFeatureEnabled(String feature) {
        return switch (feature) {
            case "DASHBOARD_SHARING" -> enableDashboardSharing;
            case "PUBLIC_DASHBOARDS" -> enablePublicDashboards;
            case "CUSTOM_REPORTS" -> enableCustomReports;
            case "DATA_COLLABORATION" -> enableDataCollaboration;
            case "ADVANCED_ANALYTICS" -> enableAdvancedAnalytics;
            case "API_ACCESS" -> enableApiAccess;
            default -> false;
        };
    }

    /**
     * Gets compliance setting value.
     */
    @SuppressWarnings("unchecked")
    public <T> T getComplianceSetting(String key, Class<T> type) {
        if (complianceSettings == null || !complianceSettings.containsKey(key)) {
            return null;
        }
        return (T) complianceSettings.get(key);
    }

    /**
     * Sets compliance setting.
     */
    public void setComplianceSetting(String key, Object value) {
        if (complianceSettings == null) {
            complianceSettings = new HashMap<>();
        }
        complianceSettings.put(key, value);
    }
}