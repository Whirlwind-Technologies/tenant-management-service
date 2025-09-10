package com.nnipa.tenant.service;

import com.nnipa.tenant.entity.Billing;
import com.nnipa.tenant.entity.BillingHistory;
import com.nnipa.tenant.entity.Invoice;
import com.nnipa.tenant.entity.Tenant;
import com.nnipa.tenant.enums.BillingCycle;
import com.nnipa.tenant.enums.BillingStatus;
import com.nnipa.tenant.enums.PaymentMethod;
import com.nnipa.tenant.event.publisher.TenantEventPublisher;
import com.nnipa.tenant.exception.BillingException;
import com.nnipa.tenant.repository.BillingRepository;
import com.nnipa.tenant.repository.InvoiceRepository;
import com.nnipa.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Service for managing tenant billing and subscriptions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingService {

    private final BillingRepository billingRepository;
    private final InvoiceRepository invoiceRepository;
    private final TenantRepository tenantRepository;
    private final TenantEventPublisher eventPublisher;

    // Pricing configuration (in production, this would come from configuration)
    private static final Map<String, BigDecimal> PLAN_PRICING = Map.of(
            "ENTERPRISE", new BigDecimal("999.00"),
            "PROFESSIONAL", new BigDecimal("299.00"),
            "STANDARD", new BigDecimal("99.00"),
            "STARTER", new BigDecimal("49.00"),
            "TRIAL", BigDecimal.ZERO
    );

    private static final Map<String, Integer> PLAN_USER_LIMITS = Map.of(
            "ENTERPRISE", Integer.MAX_VALUE,
            "PROFESSIONAL", 100,
            "STANDARD", 25,
            "STARTER", 10,
            "TRIAL", 5
    );

    /**
     * Initialize billing for a new tenant.
     */
    @Transactional
    public Billing initializeBilling(UUID tenantId, String subscriptionPlan) {
        log.info("Initializing billing for tenant: {} with plan: {}", tenantId, subscriptionPlan);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BillingException("Tenant not found: " + tenantId));

        // Create billing record
        Billing billing = Billing.builder()
                .tenantId(tenantId)
                .subscriptionPlan(subscriptionPlan)
                .billingCycle(determineBillingCycle(subscriptionPlan))
                .status(subscriptionPlan.equals("TRIAL") ? BillingStatus.TRIAL : BillingStatus.ACTIVE)
                .currentPeriodStart(Instant.now())
                .currentPeriodEnd(calculatePeriodEnd(BillingCycle.MONTHLY))
                .monthlyAmount(PLAN_PRICING.get(subscriptionPlan))
                .currency("USD")
                .paymentMethod(PaymentMethod.CREDIT_CARD) // Default
                .autoRenew(true)
                .userLimit(PLAN_USER_LIMITS.get(subscriptionPlan))
                .storageQuotaGb(determineStorageQuota(subscriptionPlan))
                .createdAt(Instant.now())
                .build();

        billing = billingRepository.save(billing);

        // Update tenant with billing info
        tenant.setSubscriptionPlan(subscriptionPlan);
        tenant.setMaxUsers(PLAN_USER_LIMITS.get(subscriptionPlan));
        tenant.setNextBillingDate(billing.getCurrentPeriodEnd());
        tenantRepository.save(tenant);

        // Create initial invoice for non-trial plans
        if (!subscriptionPlan.equals("TRIAL")) {
            createInvoice(billing, "Initial subscription");
        }

        log.info("Billing initialized for tenant: {}", tenantId);
        return billing;
    }

    /**
     * Update subscription plan.
     */
    @Transactional
    public Billing updateSubscription(UUID tenantId, String newPlan) {
        log.info("Updating subscription for tenant: {} to plan: {}", tenantId, newPlan);

        Billing billing = billingRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new BillingException("Billing not found for tenant: " + tenantId));

        String oldPlan = billing.getSubscriptionPlan();
        BigDecimal oldAmount = billing.getMonthlyAmount();
        BigDecimal newAmount = PLAN_PRICING.get(newPlan);

        // Calculate proration if needed
        if (!oldPlan.equals(newPlan)) {
            BigDecimal prorationAmount = calculateProration(billing, oldAmount, newAmount);

            if (prorationAmount.compareTo(BigDecimal.ZERO) != 0) {
                // Create adjustment invoice
                createAdjustmentInvoice(billing, prorationAmount,
                        String.format("Plan change from %s to %s", oldPlan, newPlan));
            }
        }

        // Update billing
        billing.setSubscriptionPlan(newPlan);
        billing.setMonthlyAmount(newAmount);
        billing.setUserLimit(PLAN_USER_LIMITS.get(newPlan));
        billing.setStorageQuotaGb(determineStorageQuota(newPlan));
        billing.setUpdatedAt(Instant.now());

        if (newPlan.equals("TRIAL")) {
            billing.setStatus(BillingStatus.TRIAL);
        } else if (billing.getStatus() == BillingStatus.TRIAL) {
            billing.setStatus(BillingStatus.ACTIVE);
        }

        billing = billingRepository.save(billing);

        // Update tenant
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();
        tenant.setSubscriptionPlan(newPlan);
        tenant.setMaxUsers(PLAN_USER_LIMITS.get(newPlan));
        tenantRepository.save(tenant);

        // Publish event
        eventPublisher.publishSubscriptionChangedEvent(tenant, oldPlan, newPlan);

        log.info("Subscription updated for tenant: {} from {} to {}", tenantId, oldPlan, newPlan);
        return billing;
    }

    /**
     * Cancel subscription.
     */
    @Transactional
    public void cancelSubscription(UUID tenantId) {
        log.info("Canceling subscription for tenant: {}", tenantId);

        Billing billing = billingRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new BillingException("Billing not found for tenant: " + tenantId));

        billing.setStatus(BillingStatus.CANCELLED);
        billing.setAutoRenew(false);
        billing.setCancelledAt(Instant.now());
        billing.setSubscriptionEndsAt(billing.getCurrentPeriodEnd());

        billingRepository.save(billing);

        log.info("Subscription cancelled for tenant: {}", tenantId);
    }

    /**
     * Process payment for a tenant.
     */
    @Transactional
    public Invoice processPayment(UUID tenantId, BigDecimal amount, String description) {
        log.info("Processing payment for tenant: {} amount: {}", tenantId, amount);

        Billing billing = billingRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new BillingException("Billing not found for tenant: " + tenantId));

        Invoice invoice = createInvoice(billing, description);

        try {
            // In production, this would integrate with payment gateway (Stripe, PayPal, etc.)
            boolean paymentSuccess = processPaymentWithGateway(billing, amount);

            if (paymentSuccess) {
                invoice.setStatus("PAID");
                invoice.setPaidAt(Instant.now());
                billing.setLastPaymentDate(Instant.now());
                billing.setLastPaymentAmount(amount);

                // Update next billing date
                billing.setCurrentPeriodStart(billing.getCurrentPeriodEnd());
                billing.setCurrentPeriodEnd(calculatePeriodEnd(billing.getBillingCycle()));

                // Clear any failed payment count
                billing.setFailedPaymentCount(0);
                billing.setStatus(BillingStatus.ACTIVE);
            } else {
                handleFailedPayment(billing, invoice);
            }
        } catch (Exception e) {
            log.error("Payment processing failed for tenant: {}", tenantId, e);
            handleFailedPayment(billing, invoice);
        }

        billingRepository.save(billing);
        invoiceRepository.save(invoice);

        return invoice;
    }

    /**
     * Get billing history for a tenant.
     */
    public List<Invoice> getBillingHistory(UUID tenantId) {
        return invoiceRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    /**
     * Get current billing status.
     */
    public Billing getBillingStatus(UUID tenantId) {
        return billingRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new BillingException("Billing not found for tenant: " + tenantId));
    }

    /**
     * Process recurring billing (scheduled job).
     */
    @Scheduled(cron = "0 0 2 * * ?") // Run daily at 2 AM
    @Transactional
    public void processRecurringBilling() {
        log.info("Starting recurring billing process");

        Instant now = Instant.now();
        List<Billing> dueForBilling = billingRepository.findByCurrentPeriodEndBeforeAndAutoRenewTrueAndStatusIn(
                now, List.of(BillingStatus.ACTIVE, BillingStatus.PAST_DUE)
        );

        for (Billing billing : dueForBilling) {
            try {
                processPayment(billing.getTenantId(), billing.getMonthlyAmount(), "Recurring subscription payment");
            } catch (Exception e) {
                log.error("Failed to process recurring billing for tenant: {}", billing.getTenantId(), e);
            }
        }

        log.info("Completed recurring billing process for {} tenants", dueForBilling.size());
    }

    // Helper methods

    private BillingCycle determineBillingCycle(String plan) {
        return plan.equals("ENTERPRISE") ? BillingCycle.ANNUAL : BillingCycle.MONTHLY;
    }

    private Integer determineStorageQuota(String plan) {
        return switch (plan) {
            case "ENTERPRISE" -> 1000; // 1TB
            case "PROFESSIONAL" -> 100; // 100GB
            case "STANDARD" -> 10; // 10GB
            case "STARTER" -> 5; // 5GB
            case "TRIAL" -> 1; // 1GB
            default -> 1;
        };
    }

    private Instant calculatePeriodEnd(BillingCycle cycle) {
        return switch (cycle) {
            case MONTHLY -> Instant.now().plus(30, ChronoUnit.DAYS);
            case QUARTERLY -> Instant.now().plus(90, ChronoUnit.DAYS);
            case ANNUAL -> Instant.now().plus(365, ChronoUnit.DAYS);
        };
    }

    private BigDecimal calculateProration(Billing billing, BigDecimal oldAmount, BigDecimal newAmount) {
        long daysRemaining = ChronoUnit.DAYS.between(Instant.now(), billing.getCurrentPeriodEnd());
        long totalDays = ChronoUnit.DAYS.between(billing.getCurrentPeriodStart(), billing.getCurrentPeriodEnd());

        if (totalDays <= 0) return BigDecimal.ZERO;

        BigDecimal remainingRatio = BigDecimal.valueOf(daysRemaining).divide(BigDecimal.valueOf(totalDays), 2, BigDecimal.ROUND_HALF_UP);
        BigDecimal oldCredit = oldAmount.multiply(remainingRatio);
        BigDecimal newCharge = newAmount.multiply(remainingRatio);

        return newCharge.subtract(oldCredit);
    }

    private Invoice createInvoice(Billing billing, String description) {
        return Invoice.builder()
                .tenantId(billing.getTenantId())
                .invoiceNumber(generateInvoiceNumber())
                .amount(billing.getMonthlyAmount())
                .currency(billing.getCurrency())
                .description(description)
                .status("PENDING")
                .dueDate(Instant.now().plus(7, ChronoUnit.DAYS))
                .createdAt(Instant.now())
                .build();
    }

    private Invoice createAdjustmentInvoice(Billing billing, BigDecimal amount, String description) {
        Invoice invoice = createInvoice(billing, description);
        invoice.setAmount(amount);
        invoice.setType(amount.compareTo(BigDecimal.ZERO) > 0 ? "CHARGE" : "CREDIT");
        return invoiceRepository.save(invoice);
    }

    private String generateInvoiceNumber() {
        return "INV-" + System.currentTimeMillis();
    }

    private boolean processPaymentWithGateway(Billing billing, BigDecimal amount) {
        // Simulate payment processing
        // In production, this would integrate with Stripe, PayPal, etc.
        log.info("Processing payment of {} for tenant: {}", amount, billing.getTenantId());

        // Simulate 95% success rate for demo
        return Math.random() > 0.05;
    }

    private void handleFailedPayment(Billing billing, Invoice invoice) {
        invoice.setStatus("FAILED");

        int failedCount = billing.getFailedPaymentCount() != null ? billing.getFailedPaymentCount() : 0;
        billing.setFailedPaymentCount(failedCount + 1);

        if (failedCount >= 3) {
            billing.setStatus(BillingStatus.SUSPENDED);

            // Publish payment failed event
            Tenant tenant = tenantRepository.findById(billing.getTenantId()).orElse(null);
            if (tenant != null) {
                eventPublisher.publishPaymentFailedEvent(tenant, failedCount);
            }
        } else if (failedCount >= 1) {
            billing.setStatus(BillingStatus.PAST_DUE);
        }
    }
}