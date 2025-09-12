package com.nnipa.tenant.service;

import com.nnipa.tenant.dto.request.UsageRecordRequest;
import com.nnipa.tenant.entity.Subscription;
import com.nnipa.tenant.entity.UsageRecord;
import com.nnipa.tenant.event.publisher.TenantEventPublisher;
import com.nnipa.tenant.exception.ResourceNotFoundException;
import com.nnipa.tenant.repository.BillingDetailsRepository;
import com.nnipa.tenant.repository.SubscriptionRepository;
import com.nnipa.tenant.repository.UsageRecordRepository;
import com.nnipa.tenant.repository.TenantRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for billing operations
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingService {
    private final UsageRecordRepository usageRecordRepository;
    private final BillingDetailsRepository billingDetailsRepository;
    private SubscriptionRepository subscriptionRepository;

    /**
     * Calculate current month usage
     */
    public java.math.BigDecimal calculateCurrentMonthUsage(UUID subscriptionId) {
        log.debug("Calculating current month usage for subscription: {}", subscriptionId);

        java.time.LocalDateTime startOfMonth = java.time.LocalDateTime.now()
                .withDayOfMonth(1)
                .withHour(0)
                .withMinute(0)
                .withSecond(0);

        java.time.LocalDateTime endOfMonth = startOfMonth.plusMonths(1);

        List<UsageRecord> records =
                usageRecordRepository.findBySubscriptionAndDateRange(
                        subscriptionId, startOfMonth, endOfMonth);

        return records.stream()
                .filter(com.nnipa.tenant.entity.UsageRecord::getIsBillable)
                .map(com.nnipa.tenant.entity.UsageRecord::getAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
    }

    /**
     * Calculate unbilled amount
     */
    public java.math.BigDecimal calculateUnbilledAmount(UUID subscriptionId) {
        log.debug("Calculating unbilled amount for subscription: {}", subscriptionId);

        return usageRecordRepository.calculateUnbilledAmount(subscriptionId)
                .map(java.math.BigDecimal::valueOf)
                .orElse(java.math.BigDecimal.ZERO);
    }

    /**
     * Process billing for subscription
     */
    @Transactional
    public boolean processBilling(com.nnipa.tenant.entity.Subscription subscription) {
        log.info("Processing billing for subscription: {}", subscription.getId());

        try {
            // Calculate total amount
            java.math.BigDecimal baseAmount = subscription.getMonthlyPrice();
            java.math.BigDecimal usageAmount = calculateUnbilledAmount(subscription.getId());
            java.math.BigDecimal totalAmount = baseAmount.add(usageAmount);

            // Apply discount if any
            if (subscription.getDiscountPercentage() != null) {
                java.math.BigDecimal discount = totalAmount
                        .multiply(subscription.getDiscountPercentage())
                        .divide(new java.math.BigDecimal("100"));
                totalAmount = totalAmount.subtract(discount);
            }

            // Process payment (integrate with payment gateway)
            boolean paymentSuccess = processPayment(subscription, totalAmount);

            if (paymentSuccess) {
                // Mark usage records as billed
                String invoiceId = generateInvoiceId();
                usageRecordRepository.markAsBilled(
                        subscription.getId(),
                        java.time.LocalDateTime.now(),
                        invoiceId
                );

                // Update subscription payment info
                subscription.setLastPaymentDate(java.time.LocalDateTime.now());
                subscription.setLastPaymentAmount(totalAmount);
                subscription.setLastPaymentStatus("COMPLETED");

                log.info("Billing processed successfully for subscription: {}", subscription.getId());
                return true;
            } else {
                log.warn("Payment failed for subscription: {}", subscription.getId());
                return false;
            }

        } catch (Exception e) {
            log.error("Failed to process billing for subscription: {}", subscription.getId(), e);
            return false;
        }
    }

    /**
     * Process renewal payment
     */
    public boolean processRenewalPayment(com.nnipa.tenant.entity.Subscription subscription) {
        log.info("Processing renewal payment for subscription: {}", subscription.getId());

        // Get billing details
        com.nnipa.tenant.entity.BillingDetails billingDetails =
                billingDetailsRepository.findBySubscriptionId(subscription.getId())
                        .orElse(null);

        if (billingDetails == null) {
            log.warn("No billing details found for subscription: {}", subscription.getId());
            return false;
        }

        // Calculate renewal amount based on billing cycle
        java.math.BigDecimal amount = calculateRenewalAmount(subscription);

        // Process payment
        return processPayment(subscription, amount);
    }

    /**
     * Process payment through payment gateway
     */
    private boolean processPayment(com.nnipa.tenant.entity.Subscription subscription,
                                   java.math.BigDecimal amount) {
        log.debug("Processing payment of {} for subscription: {}", amount, subscription.getId());

        // This would integrate with Stripe or other payment gateway
        // For now, simulate payment processing

        if (subscription.getStripeCustomerId() != null &&
                subscription.getStripeSubscriptionId() != null) {
            // Process through Stripe
            return processStripePayment(subscription, amount);
        }

        // Simulate successful payment for development
        return true;
    }

    /**
     * Process payment through Stripe
     */
    private boolean processStripePayment(com.nnipa.tenant.entity.Subscription subscription,
                                         java.math.BigDecimal amount) {
        // TODO: Integrate with Stripe API
        log.info("Processing Stripe payment for subscription: {}", subscription.getId());

        // This would call Stripe API to charge the customer
        // For now, return true to simulate success
        return true;
    }

    /**
     * Calculate renewal amount based on billing cycle
     */
    private java.math.BigDecimal calculateRenewalAmount(com.nnipa.tenant.entity.Subscription subscription) {
        java.math.BigDecimal baseAmount = subscription.getMonthlyPrice();

        if (baseAmount == null) {
            baseAmount = java.math.BigDecimal.ZERO;
        }

        // Adjust for billing cycle
        return switch (subscription.getBillingCycle()) {
            case MONTHLY -> baseAmount;
            case QUARTERLY -> baseAmount.multiply(new java.math.BigDecimal("3"));
            case SEMI_ANNUAL -> baseAmount.multiply(new java.math.BigDecimal("6"));
            case ANNUAL -> {
                if (subscription.getAnnualPrice() != null) {
                    yield subscription.getAnnualPrice();
                } else {
                    yield baseAmount.multiply(new java.math.BigDecimal("12"));
                }
            }
        };
    }

    /**
     * Generate invoice ID
     */
    private String generateInvoiceId() {
        return "INV-" + System.currentTimeMillis();
    }

    /**
     * Record usage
     */
    @Transactional
    public void recordUsage(UUID subscriptionId,
                            UsageRecordRequest request) {
        log.debug("Recording usage for subscription: {}", subscriptionId);

     Subscription subscription =
             subscriptionRepository
                        .findById(subscriptionId)
                        .orElseThrow(() -> new ResourceNotFoundException("Subscription not found"));

        UsageRecord record =
                UsageRecord.builder()
                        .subscription(subscription)
                        .usageDate(java.time.LocalDate.now())
                        .metricName(request.getMetricName())
                        .metricCategory(request.getMetricCategory())
                        .quantity(request.getQuantity())
                        .unit(request.getUnit())
                        .rate(request.getRate())
                        .description(request.getDescription())
                        .isBillable(request.getIsBillable() != null ? request.getIsBillable() : true)
                        .isOverage(request.getIsOverage() != null ? request.getIsOverage() : false)
                        .recordedAt(java.time.LocalDateTime.now())
                        .build();

        // Calculate amount if rate is provided
        if (record.getRate() != null && record.getQuantity() != null) {
            java.math.BigDecimal amount = record.getQuantity().multiply(record.getRate());
            record.setAmount(amount);
        }

        // Set metadata
        if (request.getMetadata() != null) {
            record.setMetadataJson(request.getMetadata());
        }

        usageRecordRepository.save(record);
        log.debug("Usage recorded for subscription: {}", subscriptionId);
    }

    /**
     * Get usage summary for a subscription
     */
    public Map<String, Object> getUsageSummary(UUID subscriptionId,
                                               java.time.LocalDateTime startDate,
                                               java.time.LocalDateTime endDate) {
        log.debug("Getting usage summary for subscription: {}", subscriptionId);

        List<Object[]> aggregatedUsage = usageRecordRepository.aggregateUsageByMetric(
                subscriptionId, startDate, endDate);

        Map<String, Object> summary = new HashMap<>();
        summary.put("subscriptionId", subscriptionId);
        summary.put("startDate", startDate);
        summary.put("endDate", endDate);

        Map<String, java.math.BigDecimal> metrics = new HashMap<>();
        for (Object[] row : aggregatedUsage) {
            String metricName = (String) row[0];
            java.math.BigDecimal total = (java.math.BigDecimal) row[1];
            metrics.put(metricName, total);
        }

        summary.put("metrics", metrics);
        summary.put("totalAmount", calculateUnbilledAmount(subscriptionId));

        return summary;
    }
}
