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
 * Flexible key-value storage for organization-specific settings.
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

    // Security Settings
    @Column(name = "password_policy", columnDefinition = "TEXT")
    private String passwordPolicy; // JSON string with policy rules

    @Column(name = "session_timeout_minutes")
    private Integer sessionTimeoutMinutes = 30;

    @Column(name = "mfa_required", nullable = false)
    private Boolean mfaRequired = false;

    @Column(name = "mfa_type", length = 50)
    private String mfaType; // TOTP, SMS, EMAIL

    @Column(name = "ip_whitelist", columnDefinition = "TEXT")
    private String ipWhitelist; // Comma-separated IP addresses/ranges

    @Column(name = "allowed_domains", columnDefinition = "TEXT")
    private String allowedDomains; // For email domain restrictions

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

    // Notification Settings
    @Column(name = "notification_email", length = 255)
    private String notificationEmail;

    @Column(name = "billing_email", length = 255)
    private String billingEmail;

    @Column(name = "technical_email", length = 255)
    private String technicalEmail;

    @Column(name = "email_notifications_enabled", nullable = false)
    private Boolean emailNotificationsEnabled = true;

    @Column(name = "sms_notifications_enabled", nullable = false)
    private Boolean smsNotificationsEnabled = false;

    @Column(name = "webhook_url", length = 500)
    private String webhookUrl;

    @Column(name = "webhook_secret", length = 255)
    private String webhookSecret;

    // Integration Settings
    @Column(name = "sso_enabled", nullable = false)
    private Boolean ssoEnabled = false;

    @Column(name = "sso_provider", length = 50)
    private String ssoProvider; // SAML, OAUTH, OIDC

    @Column(name = "sso_config", columnDefinition = "TEXT")
    private String ssoConfig; // JSON configuration

    @Column(name = "api_key", length = 255)
    private String apiKey;

    @Column(name = "api_secret", length = 255)
    private String apiSecret;

    @Column(name = "api_rate_limit_override")
    private Integer apiRateLimitOverride;

    // UI Customization
    @Column(name = "show_logo", nullable = false)
    private Boolean showLogo = true;

    @Column(name = "custom_css", columnDefinition = "TEXT")
    private String customCss;

    @Column(name = "dashboard_layout", columnDefinition = "TEXT")
    private String dashboardLayout; // JSON configuration

    @Column(name = "default_dashboard", length = 100)
    private String defaultDashboard;

    // Feature Toggles
    @Column(name = "enable_api_access", nullable = false)
    private Boolean enableApiAccess = true;

    @Column(name = "enable_data_sharing", nullable = false)
    private Boolean enableDataSharing = false;

    @Column(name = "enable_public_dashboards", nullable = false)
    private Boolean enablePublicDashboards = false;

    @Column(name = "enable_custom_reports", nullable = false)
    private Boolean enableCustomReports = true;

    @Column(name = "enable_advanced_analytics", nullable = false)
    private Boolean enableAdvancedAnalytics = false;

    // Custom Settings (JSON)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "custom_settings", columnDefinition = "jsonb")
    private Map<String, Object> customSettings = new HashMap<>();

    // Organization-specific Settings
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "compliance_settings", columnDefinition = "jsonb")
    private Map<String, Object> complianceSettings = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "workflow_settings", columnDefinition = "jsonb")
    private Map<String, Object> workflowSettings = new HashMap<>();

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
     * Checks if a feature is enabled.
     */
    public boolean isFeatureEnabled(String feature) {
        return switch (feature) {
            case "API_ACCESS" -> enableApiAccess;
            case "DATA_SHARING" -> enableDataSharing;
            case "PUBLIC_DASHBOARDS" -> enablePublicDashboards;
            case "CUSTOM_REPORTS" -> enableCustomReports;
            case "ADVANCED_ANALYTICS" -> enableAdvancedAnalytics;
            case "SSO" -> ssoEnabled;
            case "MFA" -> mfaRequired;
            default -> false;
        };
    }
}