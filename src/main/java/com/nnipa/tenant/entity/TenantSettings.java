package com.nnipa.tenant.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Where;
import org.hibernate.type.SqlTypes;

import java.util.HashMap;
import java.util.Map; /**
 * Tenant Settings entity for configuration
 */
@Entity
@Table(name = "tenant_settings", indexes = {
        @Index(name = "idx_settings_tenant", columnList = "tenant_id")
})
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Where(clause = "is_deleted = false")
public class TenantSettings extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false, unique = true)
    private Tenant tenant;

    // General Settings
    @Column(name = "timezone", length = 50)
    private String timezone;

    @Column(name = "locale", length = 10)
    private String locale;

    @Column(name = "date_format", length = 50)
    private String dateFormat;

    @Column(name = "currency", length = 3)
    private String currency;

    // Security Settings
    @Column(name = "enforce_mfa", nullable = false)
    private Boolean enforceMfa = false;

    @Column(name = "password_expiry_days")
    private Integer passwordExpiryDays;

    @Column(name = "session_timeout_minutes")
    private Integer sessionTimeoutMinutes;

    @Column(name = "ip_whitelist", columnDefinition = "TEXT")
    private String ipWhitelist;

    @Column(name = "allowed_domains", columnDefinition = "TEXT")
    private String allowedDomains;

    // Notification Settings
    @Column(name = "notification_email", length = 255)
    private String notificationEmail;

    @Column(name = "send_billing_alerts", nullable = false)
    private Boolean sendBillingAlerts = true;

    @Column(name = "send_usage_alerts", nullable = false)
    private Boolean sendUsageAlerts = true;

    @Column(name = "send_security_alerts", nullable = false)
    private Boolean sendSecurityAlerts = true;

    // Branding
    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "primary_color", length = 7)
    private String primaryColor;

    @Column(name = "secondary_color", length = 7)
    private String secondaryColor;

    // Integration Settings
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "webhook_urls", columnDefinition = "jsonb")
    private Map<String, String> webhookUrls = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "api_keys", columnDefinition = "jsonb")
    private Map<String, String> apiKeys = new HashMap<>();

    // Custom Settings
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "custom_settings", columnDefinition = "jsonb")
    private Map<String, Object> customSettings = new HashMap<>();

    // Compliance
    @Column(name = "data_retention_days")
    private Integer dataRetentionDays;

    @Column(name = "audit_log_enabled", nullable = false)
    private Boolean auditLogEnabled = true;

    @Column(name = "compliance_frameworks", length = 500)
    private String complianceFrameworks;
}
