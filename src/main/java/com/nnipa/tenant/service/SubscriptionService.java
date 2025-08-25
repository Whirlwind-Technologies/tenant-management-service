package com.nnipa.tenant.service;

import com.nnipa.tenant.entity.BillingDetails;
import com.nnipa.tenant.entity.Subscription;
import com.nnipa.tenant.entity.Tenant;
import com.nnipa.tenant.entity.UsageRecord;
import com.nnipa.tenant.enums.OrganizationType;
import com.nnipa.tenant.enums.SubscriptionPlan;
import com.nnipa.tenant.enums.TenantStatus;
import com.nnipa.tenant.repository.BillingDetailsRepository;
import com.nnipa.tenant.repository.SubscriptionRepository;
import com.nnipa.tenant.repository.TenantRepository;
import com.nnipa.tenant.repository.UsageRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing tenant subscriptions and billing.
 * Handles plan changes, billing cycles, and usage-based pricing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final TenantRepository tenantRepository;
    private final BillingDetailsRepository billingDetailsRepository;
    private final UsageRecordRepository usageRecordRepository;
    private final NotificationService notificationService;

    /**
     * Creates a new subscription for a tenant.
     */
    @Transactional
    @CacheEvict(value = "subscriptions", key = "#tenant.id")
    public Subscription createSubscription(Tenant tenant, SubscriptionPlan plan, BillingDetails billingDetails) {
        log.info("Creating subscription for tenant: {} with plan: {}", tenant.getName(), plan);

        // Check if tenant already has an active subscription
        Optional<Subscription> existing = subscriptionRepository.findByTenantAndActiveStatus(tenant);
        if (existing.isPresent()) {
            throw new SubscriptionAlreadyExistsException("Tenant already has an active subscription");
        }

        // Create subscription
        Subscription subscription = Subscription.builder()
                .tenant(tenant)
                .plan(plan)
                .subscriptionStatus("ACTIVE")
                .startDate(Instant.now())
                .currency("USD")
                .billingCycle(determineBillingCycle(tenant, plan))
                .autoRenew(true)
                .build();

        // Set pricing based on organization type and plan
        setPricing(subscription, tenant.getOrganizationType(), plan);

        // Set trial period if applicable
        if (shouldHaveTrial(tenant, plan)) {
            setTrialPeriod(subscription, tenant.getOrganizationType());
        }

        // Set renewal date
        subscription.setNextRenewalDate(calculateNextRenewalDate(subscription));

        // Save subscription
        subscription = subscriptionRepository.save(subscription);

        // Save billing details
        if (billingDetails != null) {
            billingDetails.setSubscription(subscription);
            billingDetailsRepository.save(billingDetails);
            subscription.setBillingDetails(billingDetails);
        }

        // Update tenant with subscription
        tenant.setSubscription(subscription);
        tenantRepository.save(tenant);

        // Send welcome notification
        notificationService.sendSubscriptionCreated(tenant, subscription);

        log.info("Subscription created successfully for tenant: {}", tenant.getName());
        return subscription;
    }

    /**
     * Upgrades or downgrades a subscription plan.
     */
    @Transactional
    @CacheEvict(value = "subscriptions", key = "#subscriptionId")
    public Subscription changePlan(UUID subscriptionId, SubscriptionPlan newPlan) {
        log.info("Changing subscription plan: {} to {}", subscriptionId, newPlan);

        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new SubscriptionNotFoundException("Subscription not found"));

        SubscriptionPlan currentPlan = subscription.getPlan();

        // Validate plan change
        if (!currentPlan.canUpgradeTo(newPlan)) {
            throw new InvalidPlanChangeException(
                    String.format("Cannot change from %s to %s", currentPlan, newPlan)
            );
        }

        // Calculate prorated charges/credits
        BigDecimal proratedAmount = calculateProratedAmount(subscription, newPlan);

        // Update subscription
        subscription.setPlan(newPlan);
        setPricing(subscription, subscription.getTenant().getOrganizationType(), newPlan);

        // Create usage record for plan change
        createPlanChangeRecord(subscription, currentPlan, newPlan, proratedAmount);

        subscription = subscriptionRepository.save(subscription);

        // Notify tenant
        notificationService.sendPlanChanged(subscription.getTenant(), currentPlan, newPlan);

        log.info("Subscription plan changed from {} to {}", currentPlan, newPlan);
        return subscription;
    }

    /**
     * Cancels a subscription.
     */
    @Transactional
    @CacheEvict(value = "subscriptions", key = "#subscriptionId")
    public Subscription cancelSubscription(UUID subscriptionId, String reason) {
        log.info("Canceling subscription: {} (Reason: {})", subscriptionId, reason);

        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new SubscriptionNotFoundException("Subscription not found"));

        subscription.setSubscriptionStatus("CANCELED");
        subscription.setCanceledAt(Instant.now());
        subscription.setCancellationReason(reason);
        subscription.setAutoRenew(false);

        // Set end date to end of current billing period
        subscription.setEndDate(subscription.getNextRenewalDate());

        subscription = subscriptionRepository.save(subscription);

        // Update tenant status
        Tenant tenant = subscription.getTenant();
        tenant.setStatus(TenantStatus.PENDING_DELETION);
        tenantRepository.save(tenant);

        // Notify tenant
        notificationService.sendSubscriptionCanceled(tenant, subscription);

        log.info("Subscription canceled successfully");
        return subscription;
    }

    /**
     * Renews a subscription.
     */
    @Transactional
    @CacheEvict(value = "subscriptions", key = "#subscriptionId")
    public Subscription renewSubscription(UUID subscriptionId) {
        log.info("Renewing subscription: {}", subscriptionId);

        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new SubscriptionNotFoundException("Subscription not found"));

        if (!"ACTIVE".equals(subscription.getSubscriptionStatus())) {
            throw new InvalidSubscriptionStateException("Can only renew active subscriptions");
        }

        // Process payment (would integrate with payment processor)
        BigDecimal amount = subscription.calculateMonthlyCost();
        boolean paymentSuccess = processPayment(subscription, amount);

        if (paymentSuccess) {
            // Update subscription dates
            subscription.setLastPaymentDate(Instant.now());
            subscription.setLastPaymentAmount(amount);
            subscription.setNextRenewalDate(calculateNextRenewalDate(subscription));

            // Clear trial if renewing from trial
            if (subscription.isInTrial()) {
                subscription.setTrialEndDate(Instant.now());
            }

            subscription = subscriptionRepository.save(subscription);

            // Create usage record for renewal
            createRenewalRecord(subscription, amount);

            // Notify tenant
            notificationService.sendSubscriptionRenewed(subscription.getTenant(), subscription);

            log.info("Subscription renewed successfully");
        } else {
            // Handle payment failure
            subscription.setSubscriptionStatus("PAST_DUE");
            subscription = subscriptionRepository.save(subscription);

            notificationService.sendPaymentFailed(subscription.getTenant(), subscription);
            log.warn("Subscription renewal failed due to payment issue");
        }

        return subscription;
    }

    /**
     * Records usage for a subscription.
     */
    @Transactional
    public UsageRecord recordUsage(UUID subscriptionId, String metricName, BigDecimal quantity, String unit) {
        log.debug("Recording usage for subscription: {} - {} {} {}",
                subscriptionId, quantity, unit, metricName);

        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new SubscriptionNotFoundException("Subscription not found"));

        UsageRecord record = UsageRecord.builder()
                .subscription(subscription)
                .usageDate(LocalDate.now())
                .metricName(metricName)
                .metricCategory(determineMetricCategory(metricName))
                .quantity(quantity)
                .unit(unit)
                .isBillable(true)
                .recordedAt(Instant.now())
                .build();

        // Check if usage exceeds limits
        if (isOverage(subscription, metricName, quantity)) {
            record.setIsOverage(true);
            record.setOverageQuantity(calculateOverage(subscription, metricName, quantity));
            record.setRate(getOverageRate(subscription, metricName));
        }

        // Calculate amount if billable
        if (record.getIsBillable()) {
            record.calculateAmount();
        }

        record = usageRecordRepository.save(record);

        // Check usage limits and notify if needed
        checkUsageLimits(subscription, metricName);

        return record;
    }

    /**
     * Gets current billing period usage.
     */
    @Cacheable(value = "usage", key = "#subscriptionId")
    public SubscriptionUsage getCurrentUsage(UUID subscriptionId) {
        log.debug("Getting current usage for subscription: {}", subscriptionId);

        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new SubscriptionNotFoundException("Subscription not found"));

        LocalDate startDate = LocalDate.now().withDayOfMonth(1);
        LocalDate endDate = startDate.plusMonths(1).minusDays(1);

        List<UsageRecord> records = usageRecordRepository.findBySubscriptionAndDateRange(
                subscription, startDate, endDate
        );

        return calculateUsageSummary(subscription, records);
    }

    /**
     * Scheduled task to check expiring subscriptions.
     */
    @Scheduled(cron = "0 0 2 * * *") // Run daily at 2 AM
    @Transactional
    public void checkExpiringSubscriptions() {
        log.info("Checking for expiring subscriptions");

        Instant now = Instant.now();
        Instant weekFromNow = now.plus(7, ChronoUnit.DAYS);

        List<Subscription> expiring = subscriptionRepository.findExpiringSubscriptions(now, weekFromNow);

        for (Subscription subscription : expiring) {
            // Send renewal reminder
            long daysUntilExpiry = ChronoUnit.DAYS.between(now, subscription.getNextRenewalDate());
            notificationService.sendRenewalReminder(subscription.getTenant(), subscription, (int) daysUntilExpiry);

            // Auto-renew if enabled
            if (subscription.getAutoRenew() && daysUntilExpiry <= 1) {
                try {
                    renewSubscription(subscription.getId());
                } catch (Exception e) {
                    log.error("Failed to auto-renew subscription: {}", subscription.getId(), e);
                }
            }
        }

        log.info("Processed {} expiring subscriptions", expiring.size());
    }

    /**
     * Scheduled task to check trial endings.
     */
    @Scheduled(cron = "0 0 3 * * *") // Run daily at 3 AM
    @Transactional
    public void checkTrialEndings() {
        log.info("Checking for ending trials");

        Instant now = Instant.now();
        Instant threeDaysFromNow = now.plus(3, ChronoUnit.DAYS);

        List<Subscription> endingTrials = subscriptionRepository.findEndingTrials(now, threeDaysFromNow);

        for (Subscription subscription : endingTrials) {
            long daysUntilEnd = ChronoUnit.DAYS.between(now, subscription.getTrialEndDate());
            notificationService.sendTrialEndingReminder(subscription.getTenant(), subscription, (int) daysUntilEnd);

            // Convert to paid if trial ending today
            if (daysUntilEnd == 0) {
                convertTrialToPaid(subscription);
            }
        }

        log.info("Processed {} ending trials", endingTrials.size());
    }

    // Helper methods

    private String determineBillingCycle(Tenant tenant, SubscriptionPlan plan) {
        OrganizationType orgType = tenant.getOrganizationType();

        // Government and enterprise prefer annual
        if (orgType == OrganizationType.GOVERNMENT_AGENCY ||
                orgType == OrganizationType.FINANCIAL_INSTITUTION ||
                plan == SubscriptionPlan.ENTERPRISE ||
                plan == SubscriptionPlan.GOVERNMENT) {
            return "ANNUAL";
        }

        // Academic prefers academic year
        if (orgType == OrganizationType.ACADEMIC_INSTITUTION) {
            return "ANNUAL";
        }

        // Default to monthly
        return "MONTHLY";
    }

    private void setPricing(Subscription subscription, OrganizationType orgType, SubscriptionPlan plan) {
        BigDecimal basePrice = plan.getBaseMonthlyPrice();

        if (basePrice == null) {
            // Custom pricing
            basePrice = calculateCustomPrice(orgType, plan);
        }

        // Apply organization-specific discounts
        BigDecimal discount = calculateDiscount(orgType, plan);
        if (discount.compareTo(BigDecimal.ZERO) > 0) {
            subscription.setDiscountPercentage(discount);
            subscription.setDiscountReason(getDiscountReason(orgType));
        }

        subscription.setMonthlyPrice(basePrice);

        // Calculate annual price with discount
        if ("ANNUAL".equals(subscription.getBillingCycle())) {
            BigDecimal annualPrice = basePrice.multiply(BigDecimal.valueOf(12))
                    .multiply(BigDecimal.valueOf(0.9)); // 10% annual discount
            subscription.setAnnualPrice(annualPrice);
        }
    }

    private boolean shouldHaveTrial(Tenant tenant, SubscriptionPlan plan) {
        // No trial for government or custom plans
        if (plan == SubscriptionPlan.GOVERNMENT || plan == SubscriptionPlan.CUSTOM) {
            return false;
        }

        // Individuals and startups get trials
        OrganizationType orgType = tenant.getOrganizationType();
        return orgType == OrganizationType.INDIVIDUAL ||
                orgType == OrganizationType.STARTUP ||
                plan == SubscriptionPlan.TRIAL;
    }

    private void setTrialPeriod(Subscription subscription, OrganizationType orgType) {
        int trialDays = switch (orgType) {
            case INDIVIDUAL -> 30;
            case STARTUP -> 60;
            case ACADEMIC_INSTITUTION -> 90;
            default -> 30;
        };

        subscription.setTrialStartDate(Instant.now());
        subscription.setTrialEndDate(Instant.now().plus(trialDays, ChronoUnit.DAYS));
    }

    private Instant calculateNextRenewalDate(Subscription subscription) {
        Instant baseDate = subscription.getStartDate();

        return switch (subscription.getBillingCycle()) {
            case "MONTHLY" -> baseDate.plus(30, ChronoUnit.DAYS);
            case "ANNUAL" -> baseDate.plus(365, ChronoUnit.DAYS);
            case "QUARTERLY" -> baseDate.plus(90, ChronoUnit.DAYS);
            default -> baseDate.plus(30, ChronoUnit.DAYS);
        };
    }

    private BigDecimal calculateCustomPrice(OrganizationType orgType, SubscriptionPlan plan) {
        // Custom pricing logic based on organization type
        return BigDecimal.valueOf(999.99); // Placeholder
    }

    private BigDecimal calculateDiscount(OrganizationType orgType, SubscriptionPlan plan) {
        return switch (orgType) {
            case ACADEMIC_INSTITUTION -> BigDecimal.valueOf(50);
            case NON_PROFIT -> BigDecimal.valueOf(30);
            case GOVERNMENT_AGENCY -> BigDecimal.valueOf(20);
            case STARTUP -> BigDecimal.valueOf(25);
            default -> BigDecimal.ZERO;
        };
    }

    private String getDiscountReason(OrganizationType orgType) {
        return switch (orgType) {
            case ACADEMIC_INSTITUTION -> "Academic Discount";
            case NON_PROFIT -> "Non-Profit Discount";
            case GOVERNMENT_AGENCY -> "Government Rate";
            case STARTUP -> "Startup Program";
            default -> null;
        };
    }

    private BigDecimal calculateProratedAmount(Subscription subscription, SubscriptionPlan newPlan) {
        // Calculate prorated amount for plan change
        // This is a simplified calculation
        return BigDecimal.ZERO;
    }

    private void createPlanChangeRecord(Subscription subscription, SubscriptionPlan oldPlan,
                                        SubscriptionPlan newPlan, BigDecimal amount) {
        // Create usage record for plan change
        UsageRecord record = UsageRecord.builder()
                .subscription(subscription)
                .usageDate(LocalDate.now())
                .metricName("PLAN_CHANGE")
                .description(String.format("Plan change from %s to %s", oldPlan, newPlan))
                .quantity(BigDecimal.ONE)
                .amount(amount)
                .isBillable(true)
                .recordedAt(Instant.now())
                .build();

        usageRecordRepository.save(record);
    }

    private void createRenewalRecord(Subscription subscription, BigDecimal amount) {
        UsageRecord record = UsageRecord.builder()
                .subscription(subscription)
                .usageDate(LocalDate.now())
                .metricName("SUBSCRIPTION_RENEWAL")
                .description("Subscription renewal")
                .quantity(BigDecimal.ONE)
                .amount(amount)
                .isBillable(true)
                .recordedAt(Instant.now())
                .build();

        usageRecordRepository.save(record);
    }

    private boolean processPayment(Subscription subscription, BigDecimal amount) {
        // Integration with payment processor would go here
        // For now, return true to simulate successful payment
        log.info("Processing payment of {} for subscription {}", amount, subscription.getId());
        return true;
    }

    private String determineMetricCategory(String metricName) {
        if (metricName.contains("STORAGE")) return "STORAGE";
        if (metricName.contains("API")) return "API";
        if (metricName.contains("COMPUTE")) return "COMPUTE";
        if (metricName.contains("USER")) return "USERS";
        return "DATA";
    }

    private boolean isOverage(Subscription subscription, String metricName, BigDecimal quantity) {
        // Check if usage exceeds plan limits
        return false; // Simplified
    }

    private BigDecimal calculateOverage(Subscription subscription, String metricName, BigDecimal quantity) {
        // Calculate overage amount
        return BigDecimal.ZERO; // Simplified
    }

    private BigDecimal getOverageRate(Subscription subscription, String metricName) {
        // Get overage rate for metric
        return BigDecimal.valueOf(0.01); // Simplified
    }

    private void checkUsageLimits(Subscription subscription, String metricName) {
        // Check and notify if approaching limits
    }

    private SubscriptionUsage calculateUsageSummary(Subscription subscription, List<UsageRecord> records) {
        // Calculate usage summary
        return new SubscriptionUsage(); // Simplified
    }

    private void convertTrialToPaid(Subscription subscription) {
        subscription.setSubscriptionStatus("ACTIVE");
        subscription.setTrialEndDate(Instant.now());
        subscriptionRepository.save(subscription);
        notificationService.sendTrialConverted(subscription.getTenant(), subscription);
    }

    // DTOs

    @lombok.Data
    public static class SubscriptionUsage {
        private BigDecimal totalCost;
        private BigDecimal storageUsed;
        private BigDecimal apiCallsUsed;
        private BigDecimal computeHoursUsed;
        private int activeUsers;
    }

    // Exceptions

    public static class SubscriptionNotFoundException extends RuntimeException {
        public SubscriptionNotFoundException(String message) {
            super(message);
        }
    }

    public static class SubscriptionAlreadyExistsException extends RuntimeException {
        public SubscriptionAlreadyExistsException(String message) {
            super(message);
        }
    }

    public static class InvalidPlanChangeException extends RuntimeException {
        public InvalidPlanChangeException(String message) {
            super(message);
        }
    }

    public static class InvalidSubscriptionStateException extends RuntimeException {
        public InvalidSubscriptionStateException(String message) {
            super(message);
        }
    }
}