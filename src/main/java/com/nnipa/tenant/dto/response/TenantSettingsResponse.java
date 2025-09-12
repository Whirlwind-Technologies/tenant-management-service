package com.nnipa.tenant.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID; /**
 * Response DTO for tenant settings
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TenantSettingsResponse {

    private UUID id;
    private UUID tenantId;

    // General
    private String timezone;
    private String locale;
    private String dateFormat;
    private String currency;

    // Security
    private Boolean enforceMfa;
    private Integer passwordExpiryDays;
    private Integer sessionTimeoutMinutes;
    private String ipWhitelist;
    private String allowedDomains;

    // Notifications
    private String notificationEmail;
    private Boolean sendBillingAlerts;
    private Boolean sendUsageAlerts;
    private Boolean sendSecurityAlerts;

    // Branding
    private String logoUrl;
    private String primaryColor;
    private String secondaryColor;

    // Integration
    private Map<String, String> webhookUrls;

    // Custom settings
    private Map<String, Object> customSettings;

    // Compliance
    private Integer dataRetentionDays;
    private Boolean auditLogEnabled;
    private String complianceFrameworks;

    // Audit
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
