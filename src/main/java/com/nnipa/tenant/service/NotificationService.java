package com.nnipa.tenant.service;

import com.nnipa.tenant.entity.Subscription;
import com.nnipa.tenant.entity.Tenant;
import com.nnipa.tenant.enums.SubscriptionPlan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Service for sending notifications to tenants.
 * Handles email, SMS, and webhook notifications based on tenant preferences.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    // Subscription notifications

    public void sendSubscriptionCreated(Tenant tenant, Subscription subscription) {
        log.info("Sending subscription created notification to tenant: {}", tenant.getName());
        // Implementation would send actual notification
    }

    public void sendPlanChanged(Tenant tenant, SubscriptionPlan oldPlan, SubscriptionPlan newPlan) {
        log.info("Sending plan change notification to tenant: {} ({} -> {})",
                tenant.getName(), oldPlan, newPlan);
    }

    public void sendSubscriptionCanceled(Tenant tenant, Subscription subscription) {
        log.info("Sending subscription canceled notification to tenant: {}", tenant.getName());
    }

    public void sendSubscriptionRenewed(Tenant tenant, Subscription subscription) {
        log.info("Sending subscription renewed notification to tenant: {}", tenant.getName());
    }

    public void sendPaymentFailed(Tenant tenant, Subscription subscription) {
        log.warn("Sending payment failed notification to tenant: {}", tenant.getName());
    }

    public void sendRenewalReminder(Tenant tenant, Subscription subscription, int daysUntilRenewal) {
        log.info("Sending renewal reminder to tenant: {} ({} days)",
                tenant.getName(), daysUntilRenewal);
    }

    public void sendTrialEndingReminder(Tenant tenant, Subscription subscription, int daysUntilEnd) {
        log.info("Sending trial ending reminder to tenant: {} ({} days)",
                tenant.getName(), daysUntilEnd);
    }

    public void sendTrialConverted(Tenant tenant, Subscription subscription) {
        log.info("Sending trial converted notification to tenant: {}", tenant.getName());
    }

    // Usage notifications

    public void sendUsageAlert(Tenant tenant, String message, UsageQuotaService.QuotaCheckResult quotaCheck) {
        log.warn("Sending usage alert to tenant: {} - {}", tenant.getName(), message);
    }

    public void sendOverageAlert(Tenant tenant, String resourceType, BigDecimal amount, BigDecimal cost) {
        log.warn("Sending overage alert to tenant: {} - Resource: {}, Amount: {}, Cost: {}",
                tenant.getName(), resourceType, amount, cost);
    }

    public void sendMonthlyUsageReport(Tenant tenant, UsageQuotaService.UsageStatistics stats,
                                       LocalDate startDate, LocalDate endDate) {
        log.info("Sending monthly usage report to tenant: {} for period {} to {}",
                tenant.getName(), startDate, endDate);
    }

    public void sendUpgradeRecommendation(Tenant tenant, String reason) {
        log.info("Sending upgrade recommendation to tenant: {} - Reason: {}",
                tenant.getName(), reason);
    }

    // Feature notifications

    public void sendFeatureEnabled(Tenant tenant, String featureName) {
        log.info("Sending feature enabled notification to tenant: {} - Feature: {}",
                tenant.getName(), featureName);
    }

    public void sendFeatureDisabled(Tenant tenant, String featureName) {
        log.info("Sending feature disabled notification to tenant: {} - Feature: {}",
                tenant.getName(), featureName);
    }

    public void sendFeatureTrialExpired(Tenant tenant, String featureName) {
        log.info("Sending feature trial expired notification to tenant: {} - Feature: {}",
                tenant.getName(), featureName);
    }

    // Tenant lifecycle notifications

    public void sendTenantActivated(Tenant tenant) {
        log.info("Sending tenant activated notification: {}", tenant.getName());
    }

    public void sendTenantSuspended(Tenant tenant, String reason) {
        log.warn("Sending tenant suspended notification: {} - Reason: {}",
                tenant.getName(), reason);
    }

    public void sendTenantVerified(Tenant tenant) {
        log.info("Sending tenant verified notification: {}", tenant.getName());
    }

    public void sendTenantExpiringWarning(Tenant tenant, int daysUntilExpiry) {
        log.warn("Sending tenant expiring warning: {} ({} days)",
                tenant.getName(), daysUntilExpiry);
    }

    // Security notifications

    public void sendSecurityAlert(Tenant tenant, String alertType, String details) {
        log.error("Sending security alert to tenant: {} - Type: {}, Details: {}",
                tenant.getName(), alertType, details);
    }

    public void sendComplianceAlert(Tenant tenant, String complianceIssue) {
        log.warn("Sending compliance alert to tenant: {} - Issue: {}",
                tenant.getName(), complianceIssue);
    }

    public void sendMfaEnabled(Tenant tenant) {
        log.info("Sending MFA enabled notification to tenant: {}", tenant.getName());
    }

    public void sendApiCredentialsGenerated(Tenant tenant, String apiKey) {
        log.info("Sending API credentials notification to tenant: {}", tenant.getName());
        // Note: In production, would send securely, not log the key
    }

    // System notifications

    public void sendMaintenanceNotification(Tenant tenant, LocalDate maintenanceDate, String details) {
        log.info("Sending maintenance notification to tenant: {} - Date: {}",
                tenant.getName(), maintenanceDate);
    }

    public void sendSystemUpdate(Tenant tenant, String updateType, String changes) {
        log.info("Sending system update notification to tenant: {} - Type: {}",
                tenant.getName(), updateType);
    }

    // Helper methods

    private void sendEmail(String to, String subject, String body) {
        // Email implementation would go here
        log.debug("Email sent to: {}, Subject: {}", to, subject);
    }

    private void sendSms(String phoneNumber, String message) {
        // SMS implementation would go here
        log.debug("SMS sent to: {}", phoneNumber);
    }

    private void sendWebhook(String url, Object payload) {
        // Webhook implementation would go here
        log.debug("Webhook sent to: {}", url);
    }

    private boolean shouldSendNotification(Tenant tenant, String notificationType) {
        // Check tenant notification preferences
        if (tenant.getSettings() == null) {
            return true; // Default to sending if no preferences
        }

        return switch (notificationType) {
            case "EMAIL" -> tenant.getSettings().getEmailNotificationsEnabled();
            case "SMS" -> tenant.getSettings().getSmsNotificationsEnabled();
            case "WEBHOOK" -> tenant.getSettings().getWebhookUrl() != null;
            default -> true;
        };
    }
}