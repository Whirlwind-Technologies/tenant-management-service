package com.nnipa.tenant.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map; /**
 * Request DTO for tenant settings
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TenantSettingsRequest {

    // General
    private String timezone;
    private String locale;
    private String dateFormat;
    private String currency;

    // Security
    private Boolean enforceMfa;

    @Min(0)
    private Integer passwordExpiryDays;

    @Min(1)
    private Integer sessionTimeoutMinutes;

    private String ipWhitelist;
    private String allowedDomains;

    // Notifications
    @Email
    private String notificationEmail;

    private Boolean sendBillingAlerts;
    private Boolean sendUsageAlerts;
    private Boolean sendSecurityAlerts;

    // Branding
    private String logoUrl;

    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$")
    private String primaryColor;

    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$")
    private String secondaryColor;

    // Integration
    private Map<String, String> webhookUrls;
    private Map<String, String> apiKeys;

    // Custom settings
    private Map<String, Object> customSettings;

    // Compliance
    @Min(1)
    private Integer dataRetentionDays;

    private Boolean auditLogEnabled;
    private String complianceFrameworks;
}
