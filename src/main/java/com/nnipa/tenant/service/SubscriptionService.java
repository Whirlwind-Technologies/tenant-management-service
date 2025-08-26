package com.nnipa.tenant.service;

import com.nnipa.tenant.client.NotificationServiceClient;
import com.nnipa.tenant.dto.request.RecordUsageRequest;
import com.nnipa.tenant.entity.*;
import com.nnipa.tenant.enums.SubscriptionPlan;
import com.nnipa.tenant.repository.SubscriptionRepository;
import com.nnipa.tenant.repository.UsageRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Service for managing subscriptions and billing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final UsageRecordRepository usageRecordRepository;
    private final NotificationServiceClient notificationClient;

    /**
     * Creates a new subscription for a tenant.
     */
    @Transactional
    public Subscription createSubscription(Tenant tenant, SubscriptionPlan plan, BillingDetails billingDetails) {
        log.info("Creating subscription for tenant: {} with plan: {}", tenant.getName(), plan);

        // Check for existing active subscription
        Optional<Subscription> existing = subscriptionRepository.findByTenantAndActiveStatus(tenant);
        if (existing.isPresent()) {
            throw new IllegalStateException("Tenant already has an active subscription");
        }

        Subscription subscription = Subscription.builder()
                .tenant(tenant)
                .plan(plan)
                .subscriptionStatus("ACTIVE")
                .startDate(Instant.now())
                .currency("USD")
                .billingCycle(plan == SubscriptionPlan.GOVERNMENT ? "ANNUAL" : "MONTHLY")
                .monthlyPrice(plan.getBaseMonthlyPrice())
                .autoRenew(true)
                .build();

        // Set trial if applicable
        if (plan.getTrialDays() > 0) {
            subscription.setTrialStartDate(Instant.now());
            subscription.setTrialEndDate(Instant.now().plus(plan.getTrialDays(), ChronoUnit.DAYS));
        }

        // Set next renewal date
        subscription.setNextRenewalDate(calculateNextRenewalDate(subscription));

        // Apply organization-specific discounts
        applyOrganizationDiscounts(subscription, tenant);

        // Set billing details
        if (billingDetails != null) {
            billingDetails.setSubscription(subscription);
            subscription.setBillingDetails(billingDetails);
        }

        subscription = subscriptionRepository.save(subscription);

        // Send notification
        notificationClient.sendNotification(
                tenant.getId(),
                NotificationServiceClient.NotificationType.SUBSCRIPTION_CREATED,
                Map.of("plan", plan.name(), "price", subscription.getMonthlyPrice())
        );

        log.info("Subscription created successfully for tenant: {}", tenant.getName());
        return subscription;
    }

    /**
     * Gets a subscription by ID.
     */
    public Optional<Subscription> getSubscriptionById(UUID id) {
        return subscriptionRepository.findById(id);
    }

    /**
     * Gets active subscription for a tenant.
     */
    public Optional<Subscription> getTenantActiveSubscription(UUID tenantId) {
        // This would need to be implemented with proper repository method
        return subscriptionRepository.findAll().stream()
                .filter(s -> s.getTenant().getId().equals(tenantId))
                .filter(s -> "ACTIVE".equals(s.getSubscriptionStatus()))
                .findFirst();
    }

    /**
     * Changes subscription plan.
     */
    @Transactional
    public Subscription changePlan(UUID subscriptionId, SubscriptionPlan newPlan) {
        log.info("Changing plan for subscription: {} to {}", subscriptionId, newPlan);

        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new RuntimeException("Subscription not found"));

        SubscriptionPlan oldPlan = subscription.getPlan();

        // Validate plan change
        if (!canChangePlan(oldPlan, newPlan)) {
            throw new IllegalStateException("Cannot change from " + oldPlan + " to " + newPlan);
        }

        subscription.setPlan(newPlan);
        subscription.setMonthlyPrice(newPlan.getBaseMonthlyPrice());

        // Recalculate discounts
        applyOrganizationDiscounts(subscription, subscription.getTenant());

        subscription = subscriptionRepository.save(subscription);

        // Send notification
        notificationClient.sendNotification(
                subscription.getTenant().getId(),
                NotificationServiceClient.NotificationType.SUBSCRIPTION_CHANGED,
                Map.of("oldPlan", oldPlan, "newPlan", newPlan)
        );

        log.info("Plan changed successfully from {} to {}", oldPlan, newPlan);
        return subscription;
    }

    /**
     * Cancels a subscription.
     */
    @Transactional
    public Subscription cancelSubscription(UUID subscriptionId, String reason) {
        log.info("Canceling subscription: {} - Reason: {}", subscriptionId, reason);

        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new RuntimeException("Subscription not found"));

        subscription.setSubscriptionStatus("CANCELED");
        subscription.setCanceledAt(Instant.now());
        subscription.setCancellationReason(reason);
        subscription.setAutoRenew(false);
        subscription.setEndDate(subscription.getNextRenewalDate());

        subscription = subscriptionRepository.save(subscription);

        // Send notification
        notificationClient.sendNotification(
                subscription.getTenant().getId(),
                NotificationServiceClient.NotificationType.SUBSCRIPTION_CANCELLED,
                Map.of("reason", reason)
        );

        log.info("Subscription canceled successfully");
        return subscription;
    }

    /**
     * Renews a subscription.
     */
    @Transactional
    public Subscription renewSubscription(UUID subscriptionId) {
        log.info("Renewing subscription: {}", subscriptionId);

        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new RuntimeException("Subscription not found"));

        if (!"ACTIVE".equals(subscription.getSubscriptionStatus())) {
            throw new IllegalStateException("Cannot renew inactive subscription");
        }

        subscription.setLastPaymentDate(Instant.now());
        subscription.setLastPaymentAmount(subscription.getMonthlyPrice());
        subscription.setNextRenewalDate(calculateNextRenewalDate(subscription));

        subscription = subscriptionRepository.save(subscription);

        // Send notification
        notificationClient.sendNotification(
                subscription.getTenant().getId(),
                NotificationServiceClient.NotificationType.SUBSCRIPTION_RENEWED,
                Map.of("nextRenewalDate", subscription.getNextRenewalDate())
        );

        log.info("Subscription renewed successfully");
        return subscription;
    }

    /**
     * Records usage for a subscription - Fixed version.
     * Now accepts individual parameters instead of RecordUsageRequest.
     */
    @Transactional
    public UsageRecord recordUsage(UUID subscriptionId, String metricName,
                                   BigDecimal quantity, String unit) {
        log.debug("Recording usage: {} {} {} for subscription: {}",
                quantity, unit, metricName, subscriptionId);

        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new RuntimeException("Subscription not found"));

        UsageRecord record = UsageRecord.builder()
                .subscription(subscription)
                .usageDate(LocalDate.now())
                .metricName(metricName)
                .metricCategory(determineCategory(metricName))
                .quantity(quantity)
                .unit(unit)
                .isBillable(true)
                .recordedAt(Instant.now())
                .build();

        // Calculate amount if rate is known
        BigDecimal rate = getUsageRate(subscription.getPlan(), metricName);
        if (rate != null) {
            record.setRate(rate);
            record.setAmount(quantity.multiply(rate));
        }

        record = usageRecordRepository.save(record);
        log.debug("Usage recorded successfully");
        return record;
    }

    /**
     * Alternative recordUsage method that accepts RecordUsageRequest.
     * This creates a bridge between the controller and the service.
     */
    @Transactional
    public UsageRecord recordUsage(UUID subscriptionId, RecordUsageRequest request) {
        return recordUsage(
                subscriptionId,
                request.getMetricName(),
                request.getQuantity(),
                request.getUnit()
        );
    }

    /**
     * Gets usage records for a date range.
     */
    public List<UsageRecord> getUsageRecords(UUID subscriptionId, LocalDate startDate, LocalDate endDate) {
        log.debug("Fetching usage records for subscription: {} from {} to {}",
                subscriptionId, startDate, endDate);

        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new RuntimeException("Subscription not found"));

        return usageRecordRepository.findBySubscriptionAndDateRange(subscription, startDate, endDate);
    }

    /**
     * Gets current usage statistics.
     */
    public SubscriptionUsage getCurrentUsage(UUID subscriptionId) {
        log.debug("Getting current usage for subscription: {}", subscriptionId);

        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new RuntimeException("Subscription not found"));

        LocalDate startDate = LocalDate.now().withDayOfMonth(1);
        LocalDate endDate = LocalDate.now();

        List<UsageRecord> records = usageRecordRepository.findBySubscriptionAndDateRange(
                subscription, startDate, endDate);

        Map<String, BigDecimal> usageByMetric = new HashMap<>();
        BigDecimal totalCost = BigDecimal.ZERO;

        for (UsageRecord record : records) {
            usageByMetric.merge(record.getMetricName(), record.getQuantity(), BigDecimal::add);
            if (record.getAmount() != null) {
                totalCost = totalCost.add(record.getAmount());
            }
        }

        return new SubscriptionUsage(
                subscriptionId,
                startDate,
                endDate,
                usageByMetric,
                totalCost
        );
    }

    // Helper methods

    private Instant calculateNextRenewalDate(Subscription subscription) {
        if ("ANNUAL".equals(subscription.getBillingCycle())) {
            return Instant.now().plus(365, ChronoUnit.DAYS);
        } else {
            return Instant.now().plus(30, ChronoUnit.DAYS);
        }
    }

    private void applyOrganizationDiscounts(Subscription subscription, Tenant tenant) {
        BigDecimal discount = BigDecimal.ZERO;
        String discountReason = null;

        switch (tenant.getOrganizationType()) {
            case ACADEMIC_INSTITUTION -> {
                discount = new BigDecimal("50");
                discountReason = "Academic discount";
            }
            case NON_PROFIT -> {
                discount = new BigDecimal("30");
                discountReason = "Non-profit discount";
            }
            case GOVERNMENT_AGENCY -> {
                discount = new BigDecimal("20");
                discountReason = "Government discount";
            }
            case STARTUP -> {
                discount = new BigDecimal("25");
                discountReason = "Startup discount";
            }
        }

        if (discount.compareTo(BigDecimal.ZERO) > 0) {
            subscription.setDiscountPercentage(discount);
            subscription.setDiscountReason(discountReason);

            BigDecimal discountAmount = subscription.getMonthlyPrice()
                    .multiply(discount)
                    .divide(new BigDecimal("100"));
            subscription.setMonthlyPrice(
                    subscription.getMonthlyPrice().subtract(discountAmount)
            );
        }
    }

    private boolean canChangePlan(SubscriptionPlan oldPlan, SubscriptionPlan newPlan) {
        // Basic validation - can be enhanced
        if (oldPlan == SubscriptionPlan.TRIAL && newPlan == SubscriptionPlan.FREEMIUM) {
            return false; // Cannot downgrade from trial to freemium
        }
        return true;
    }

    private String determineCategory(String metricName) {
        if (metricName.contains("STORAGE")) return "STORAGE";
        if (metricName.contains("API")) return "API";
        if (metricName.contains("COMPUTE")) return "COMPUTE";
        if (metricName.contains("USER")) return "USERS";
        return "OTHER";
    }

    private BigDecimal getUsageRate(SubscriptionPlan plan, String metricName) {
        // Simplified rate calculation - would be more complex in production
        return switch (metricName) {
            case "API_CALLS" -> new BigDecimal("0.0001");
            case "STORAGE_GB" -> new BigDecimal("0.10");
            case "COMPUTE_HOURS" -> new BigDecimal("0.50");
            default -> BigDecimal.ZERO;
        };
    }

    // Inner class for usage statistics
    public static class SubscriptionUsage {
        private final UUID subscriptionId;
        private final LocalDate startDate;
        private final LocalDate endDate;
        private final Map<String, BigDecimal> usageByMetric;
        private final BigDecimal totalCost;

        public SubscriptionUsage(UUID subscriptionId, LocalDate startDate, LocalDate endDate,
                                 Map<String, BigDecimal> usageByMetric, BigDecimal totalCost) {
            this.subscriptionId = subscriptionId;
            this.startDate = startDate;
            this.endDate = endDate;
            this.usageByMetric = usageByMetric;
            this.totalCost = totalCost;
        }

        // Getters
        public UUID getSubscriptionId() { return subscriptionId; }
        public LocalDate getStartDate() { return startDate; }
        public LocalDate getEndDate() { return endDate; }
        public Map<String, BigDecimal> getUsageByMetric() { return usageByMetric; }
        public BigDecimal getTotalCost() { return totalCost; }
    }
}