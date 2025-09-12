package com.nnipa.tenant.service;

import com.nnipa.tenant.dto.request.CreateTenantRequest;
import com.nnipa.tenant.dto.request.UpdateSubscriptionRequest;
import com.nnipa.tenant.dto.response.SubscriptionResponse;
import com.nnipa.tenant.entity.Subscription;
import com.nnipa.tenant.entity.Tenant;
import com.nnipa.tenant.enums.BillingCycle;
import com.nnipa.tenant.enums.SubscriptionPlan;
import com.nnipa.tenant.enums.SubscriptionStatus;
import com.nnipa.tenant.event.publisher.TenantEventPublisher;
import com.nnipa.tenant.exception.ResourceNotFoundException;
import com.nnipa.tenant.mapper.SubscriptionMapper;
import com.nnipa.tenant.repository.SubscriptionRepository;
import com.nnipa.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing subscriptions
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final TenantRepository tenantRepository;
    private final SubscriptionMapper subscriptionMapper;
    private final TenantEventPublisher eventPublisher;
    private final BillingService billingService;

    /**
     * Create subscription for a new tenant
     */
    @Transactional
    public Subscription createSubscription(Tenant tenant, CreateTenantRequest request) {
        log.info("Creating subscription for tenant: {}", tenant.getTenantCode());

        Subscription subscription = Subscription.builder()
                .tenant(tenant)
                .plan(request.getSubscriptionPlan())
                .subscriptionStatus(SubscriptionStatus.PENDING)
                .billingCycle(request.getBillingCycle() != null ?
                        request.getBillingCycle() : BillingCycle.MONTHLY)
                .monthlyPrice(request.getMonthlyPrice() != null ?
                        request.getMonthlyPrice() : getDefaultPrice(request.getSubscriptionPlan()))
                .currency(request.getCurrency() != null ? request.getCurrency() : "USD")
                .startDate(LocalDateTime.now())
                .autoRenew(true)
                .build();

        // Set trial dates if trial plan
        if (request.getSubscriptionPlan().name().equals("TRIAL")) {
            subscription.setTrialStartDate(LocalDateTime.now());
            subscription.setTrialEndDate(request.getTrialEndsAt() != null ?
                    request.getTrialEndsAt() : LocalDateTime.now().plusDays(30));
            subscription.setSubscriptionStatus(SubscriptionStatus.TRIALING);
        }

        // Calculate next renewal date
        subscription.setNextRenewalDate(calculateNextRenewalDate(subscription));

        subscription = subscriptionRepository.save(subscription);
        log.info("Subscription created with ID: {}", subscription.getId());

        return subscription;
    }

    /**
     * Get subscription by tenant ID
     */
    @Cacheable(value = "subscriptions", key = "#tenantId")
    public SubscriptionResponse getSubscriptionByTenantId(UUID tenantId) {
        log.debug("Fetching subscription for tenant: {}", tenantId);

        Subscription subscription = subscriptionRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found for tenant: " + tenantId));

        // Calculate current usage if needed
        BigDecimal currentMonthUsage = billingService.calculateCurrentMonthUsage(subscription.getId());
        BigDecimal unbilledAmount = billingService.calculateUnbilledAmount(subscription.getId());

        SubscriptionResponse response = subscriptionMapper.toResponse(subscription);
        response.setCurrentMonthUsage(currentMonthUsage);
        response.setUnbilledAmount(unbilledAmount);

        return response;
    }

    /**
     * Update subscription
     */
    @Transactional
    @CacheEvict(value = "subscriptions", key = "#tenantId")
    public SubscriptionResponse updateSubscription(UUID tenantId, UpdateSubscriptionRequest request, String updatedBy) {
        log.info("Updating subscription for tenant: {}", tenantId);

        Subscription subscription = subscriptionRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found for tenant: " + tenantId));

        String oldPlan = subscription.getPlan().name();

        // Update fields
        if (request.getPlan() != null) {
            subscription.setPlan(request.getPlan());
        }
        if (request.getBillingCycle() != null) {
            subscription.setBillingCycle(request.getBillingCycle());
        }
        if (request.getMonthlyPrice() != null) {
            subscription.setMonthlyPrice(request.getMonthlyPrice());
        }
        if (request.getAutoRenew() != null) {
            subscription.setAutoRenew(request.getAutoRenew());
        }

        // Update discount information
        if (request.getDiscountPercentage() != null) {
            subscription.setDiscountPercentage(request.getDiscountPercentage());
        }
        if (request.getDiscountReason() != null) {
            subscription.setDiscountReason(request.getDiscountReason());
        }

        // Update external references
        if (request.getStripeSubscriptionId() != null) {
            subscription.setStripeSubscriptionId(request.getStripeSubscriptionId());
        }

        subscription.setUpdatedBy(updatedBy);
        subscription = subscriptionRepository.save(subscription);

        // Publish event if plan changed
        if (!oldPlan.equals(subscription.getPlan().name())) {
            eventPublisher.publishSubscriptionChangedEvent(subscription, oldPlan, updatedBy);
        }

        return subscriptionMapper.toResponse(subscription);
    }

    /**
     * Activate subscription
     */
    @Transactional
    public void activateSubscription(UUID tenantId) {
        log.info("Activating subscription for tenant: {}", tenantId);

        Subscription subscription = subscriptionRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found"));

        subscription.activate();
        subscriptionRepository.save(subscription);
    }

    /**
     * Cancel subscription
     */
    @Transactional
    @CacheEvict(value = "subscriptions", key = "#tenantId")
    public void cancelSubscription(UUID tenantId, String reason) {
        log.info("Cancelling subscription for tenant: {}, reason: {}", tenantId, reason);

        Subscription subscription = subscriptionRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found"));

        subscription.cancel(reason);
        subscriptionRepository.save(subscription);
    }

    /**
     * Pause subscription
     */
    @Transactional
    public void pauseSubscription(UUID tenantId) {
        log.info("Pausing subscription for tenant: {}", tenantId);

        Subscription subscription = subscriptionRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found"));

        subscription.setSubscriptionStatus(SubscriptionStatus.PAUSED);
        subscriptionRepository.save(subscription);
    }

    /**
     * Resume subscription
     */
    @Transactional
    public void resumeSubscription(UUID tenantId) {
        log.info("Resuming subscription for tenant: {}", tenantId);

        Subscription subscription = subscriptionRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found"));

        subscription.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
        subscriptionRepository.save(subscription);
    }

    /**
     * Convert trial to paid subscription
     */
    @Transactional
    public void convertTrialToPaid(UUID tenantId) {
        log.info("Converting trial to paid subscription for tenant: {}", tenantId);

        Subscription subscription = subscriptionRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found"));

        if (!subscription.isTrialing()) {
            log.warn("Subscription is not in trial status");
            return;
        }

        // Convert to basic plan by default
        subscription.setPlan(com.nnipa.tenant.enums.SubscriptionPlan.BASIC);
        subscription.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
        subscription.setTrialEndDate(LocalDateTime.now());
        subscription.setStartDate(LocalDateTime.now());
        subscription.setNextRenewalDate(calculateNextRenewalDate(subscription));

        subscriptionRepository.save(subscription);

        // Trigger billing
        billingService.processBilling(subscription);
    }

    /**
     * Process subscription renewals (scheduled job)
     */
    @Scheduled(cron = "0 0 2 * * ?") // Run at 2 AM every day
    @Transactional
    public void processSubscriptionRenewals() {
        log.info("Processing subscription renewals");

        LocalDateTime now = LocalDateTime.now();
        List<Subscription> subscriptionsToRenew =
                subscriptionRepository.findSubscriptionsToRenew(now);

        for (Subscription subscription : subscriptionsToRenew) {
            try {
                log.info("Renewing subscription: {}", subscription.getId());

                // Process payment
                boolean paymentSuccess = billingService.processRenewalPayment(subscription);

                if (paymentSuccess) {
                    subscription.renew();
                    subscription.setFailedPaymentCount(0);
                } else {
                    subscription.setFailedPaymentCount(subscription.getFailedPaymentCount() + 1);

                    // Suspend after 3 failed attempts
                    if (subscription.getFailedPaymentCount() >= 3) {
                        subscription.setSubscriptionStatus(SubscriptionStatus.PAST_DUE);
//                        eventPublisher.publishBillingFailedEvent(subscription);
                    }
                }

                subscriptionRepository.save(subscription);

            } catch (Exception e) {
                log.error("Failed to renew subscription: {}", subscription.getId(), e);
            }
        }

        log.info("Processed {} subscription renewals", subscriptionsToRenew.size());
    }

    /**
     * Process expiring trials (scheduled job)
     */
    @Scheduled(cron = "0 0 10 * * ?") // Run at 10 AM every day
    public void processExpiringTrials() {
        log.info("Processing expiring trials");

        LocalDateTime tomorrow = LocalDateTime.now().plusDays(1);
        List<Subscription> expiringTrials =
                subscriptionRepository.findExpiringTrials(tomorrow);

        for (Subscription subscription : expiringTrials) {
            log.info("Trial expiring for subscription: {}", subscription.getId());

            // Send notification
//            eventPublisher.publishTrialExpiringEvent(subscription.getTenant());

            // Mark as notified
            subscription.setRenewalReminderSent(true);
            subscriptionRepository.save(subscription);
        }

        log.info("Processed {} expiring trials", expiringTrials.size());
    }

    /**
     * Calculate next renewal date based on billing cycle
     */
    private LocalDateTime calculateNextRenewalDate(Subscription subscription) {
        LocalDateTime baseDate = subscription.getStartDate() != null ?
                subscription.getStartDate() : LocalDateTime.now();

        return switch (subscription.getBillingCycle()) {
            case MONTHLY -> baseDate.plusMonths(1);
            case QUARTERLY -> baseDate.plusMonths(3);
            case SEMI_ANNUAL -> baseDate.plusMonths(6);
            case ANNUAL -> baseDate.plusYears(1);
        };
    }

    /**
     * Get default price for plan
     */
    private BigDecimal getDefaultPrice(SubscriptionPlan plan) {
        return switch (plan) {
            case FREEMIUM, TRIAL -> BigDecimal.ZERO;
            case BASIC -> new BigDecimal("29.99");
            case PROFESSIONAL -> new BigDecimal("99.99");
            case ENTERPRISE -> new BigDecimal("299.99");
            case GOVERNMENT -> new BigDecimal("499.99");
            case ACADEMIC -> new BigDecimal("49.99");
            case CUSTOM -> new BigDecimal("999.99");
        };
    }
}