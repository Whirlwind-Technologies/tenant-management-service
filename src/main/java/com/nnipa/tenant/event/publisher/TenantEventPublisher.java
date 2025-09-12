package com.nnipa.tenant.event.publisher;

import com.google.protobuf.Timestamp;
import com.nnipa.proto.common.EventMetadata;
import com.nnipa.proto.common.Priority;
import com.nnipa.proto.tenant.*;
import com.nnipa.tenant.entity.FeatureFlag;
import com.nnipa.tenant.entity.Subscription;
import com.nnipa.tenant.entity.Tenant;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Publisher for tenant-related events
 * Handles all outgoing events for tenant lifecycle, subscription changes, and feature flag updates
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantEventPublisher {

    private final KafkaTemplate<String, byte[]> kafkaTemplate;

    @Value("${spring.application.name}")
    private String applicationName;

    @Value("${kafka.topics.tenant-created}")
    private String tenantCreatedTopic;

    @Value("${kafka.topics.tenant-updated}")
    private String tenantUpdatedTopic;

    @Value("${kafka.topics.tenant-activated}")
    private String tenantActivatedTopic;

    @Value("${kafka.topics.tenant-suspended}")
    private String tenantSuspendedTopic;

    @Value("${kafka.topics.tenant-reactivated}")
    private String tenantReactivatedTopic;

    @Value("${kafka.topics.tenant-deleted}")
    private String tenantDeletedTopic;

    @Value("${kafka.topics.subscription-created}")
    private String subscriptionCreatedTopic;

    @Value("${kafka.topics.subscription-changed}")
    private String subscriptionChangedTopic;

    @Value("${kafka.topics.subscription-cancelled}")
    private String subscriptionCancelledTopic;

    @Value("${kafka.topics.subscription-renewed}")
    private String subscriptionRenewedTopic;

    @Value("${kafka.topics.billing-failed}")
    private String billingFailedTopic;

    @Value("${kafka.topics.feature-enabled}")
    private String featureEnabledTopic;

    @Value("${kafka.topics.feature-disabled}")
    private String featureDisabledTopic;

    @Value("${kafka.topics.feature-updated}")
    private String featureUpdatedTopic;

    /**
     * Publish tenant created event
     */
    @CircuitBreaker(name = "kafka-producer", fallbackMethod = "handlePublishFailure")
    public void publishTenantCreatedEvent(Tenant tenant) {
        log.info("Publishing tenant created event for tenant: {}", tenant.getTenantCode());

        try {
            TenantCreatedEvent event = TenantCreatedEvent.newBuilder()
                    .setMetadata(createEventMetadata(tenant.getId().toString(), tenant.getCreatedBy()))
                    .setTenant(buildTenantData(tenant))
                    .build();

            publishEvent(tenantCreatedTopic, tenant.getId().toString(), event.toByteArray());

            log.info("Successfully published tenant created event for: {}", tenant.getTenantCode());
        } catch (Exception e) {
            log.error("Failed to publish tenant created event for: {}", tenant.getTenantCode(), e);
            throw e;
        }
    }

    /**
     * Publish tenant updated event
     */
    @CircuitBreaker(name = "kafka-producer", fallbackMethod = "handlePublishFailure")
    public void publishTenantUpdatedEvent(Tenant tenant) {
        log.info("Publishing tenant updated event for tenant: {}", tenant.getTenantCode());

        try {
            TenantUpdatedEvent event = TenantUpdatedEvent.newBuilder()
                    .setMetadata(createEventMetadata(tenant.getId().toString(), tenant.getUpdatedBy()))
                    .setTenant(buildTenantData(tenant))
                    .setUpdatedAt(toTimestamp(tenant.getUpdatedAt()))
                    .setUpdatedBy(tenant.getUpdatedBy() != null ? tenant.getUpdatedBy() : "system")
                    .build();

            publishEvent(tenantUpdatedTopic, tenant.getId().toString(), event.toByteArray());

            log.info("Successfully published tenant updated event for: {}", tenant.getTenantCode());
        } catch (Exception e) {
            log.error("Failed to publish tenant updated event for: {}", tenant.getTenantCode(), e);
            throw e;
        }
    }

    /**
     * Publish tenant activated event
     */
    @CircuitBreaker(name = "kafka-producer", fallbackMethod = "handlePublishFailure")
    public void publishTenantActivatedEvent(Tenant tenant, String activatedBy) {
        log.info("Publishing tenant activated event for tenant: {}", tenant.getTenantCode());

        try {
            TenantActivatedEvent event = TenantActivatedEvent.newBuilder()
                    .setMetadata(createEventMetadata(tenant.getId().toString(), activatedBy))
                    .setTenantId(tenant.getId().toString())
                    .setTenantCode(tenant.getTenantCode())
                    .setActivatedBy(activatedBy != null ? activatedBy : "system")
                    .setActivatedAt(toTimestamp(tenant.getActivatedAt()))
                    .build();

            publishEvent(tenantActivatedTopic, tenant.getId().toString(), event.toByteArray());

            log.info("Successfully published tenant activated event for: {}", tenant.getTenantCode());
        } catch (Exception e) {
            log.error("Failed to publish tenant activated event for: {}", tenant.getTenantCode(), e);
            throw e;
        }
    }

    /**
     * Publish tenant suspended event
     */
    @CircuitBreaker(name = "kafka-producer", fallbackMethod = "handlePublishFailure")
    public void publishTenantSuspendedEvent(Tenant tenant, String reason) {
        log.info("Publishing tenant suspended event for tenant: {}", tenant.getTenantCode());

        try {
            TenantSuspendedEvent event = TenantSuspendedEvent.newBuilder()
                    .setMetadata(createEventMetadata(tenant.getId().toString(), tenant.getUpdatedBy()))
                    .setTenantId(tenant.getId().toString())
                    .setTenantCode(tenant.getTenantCode())
                    .setReason(reason != null ? reason : "Suspension requested")
                    .setSuspendedAt(toTimestamp(tenant.getSuspendedAt()))
                    .setSuspendedBy(tenant.getUpdatedBy() != null ? tenant.getUpdatedBy() : "system")
                    .build();

            publishEvent(tenantSuspendedTopic, tenant.getId().toString(), event.toByteArray());

            log.info("Successfully published tenant suspended event for: {}", tenant.getTenantCode());
        } catch (Exception e) {
            log.error("Failed to publish tenant suspended event for: {}", tenant.getTenantCode(), e);
            throw e;
        }
    }

    /**
     * Publish tenant reactivated event
     */
    @CircuitBreaker(name = "kafka-producer", fallbackMethod = "handlePublishFailure")
    public void publishTenantReactivatedEvent(Tenant tenant, String reactivatedBy) {
        log.info("Publishing tenant reactivated event for tenant: {}", tenant.getTenantCode());

        try {
            TenantReactivatedEvent event = TenantReactivatedEvent.newBuilder()
                    .setMetadata(createEventMetadata(tenant.getId().toString(), reactivatedBy))
                    .setTenantId(tenant.getId().toString())
                    .setReactivatedAt(toTimestamp(LocalDateTime.now()))
                    .setReactivatedBy(reactivatedBy != null ? reactivatedBy : "system")
                    .build();

            publishEvent(tenantReactivatedTopic, tenant.getId().toString(), event.toByteArray());

            log.info("Successfully published tenant reactivated event for: {}", tenant.getTenantCode());
        } catch (Exception e) {
            log.error("Failed to publish tenant reactivated event for: {}", tenant.getTenantCode(), e);
            throw e;
        }
    }

    /**
     * Publish tenant deleted event
     */
    @CircuitBreaker(name = "kafka-producer", fallbackMethod = "handlePublishFailure")
    public void publishTenantDeletedEvent(Tenant tenant, String deletedBy) {
        log.info("Publishing tenant deleted event for tenant: {}", tenant.getTenantCode());

        try {
            TenantDeletedEvent event = TenantDeletedEvent.newBuilder()
                    .setMetadata(createEventMetadata(tenant.getId().toString(), deletedBy))
                    .setTenantId(tenant.getId().toString())
                    .setTenantCode(tenant.getTenantCode())
                    .setDeletedBy(deletedBy != null ? deletedBy : "system")
                    .setDeletedAt(toTimestamp(tenant.getDeletedAt()))
                    .setPermanent(true)
                    .build();

            publishEvent(tenantDeletedTopic, tenant.getId().toString(), event.toByteArray());

            log.info("Successfully published tenant deleted event for: {}", tenant.getTenantCode());
        } catch (Exception e) {
            log.error("Failed to publish tenant deleted event for: {}", tenant.getTenantCode(), e);
            throw e;
        }
    }

    /**
     * Publish subscription created event
     */
    @CircuitBreaker(name = "kafka-producer", fallbackMethod = "handlePublishFailure")
    public void publishSubscriptionCreatedEvent(Subscription subscription) {
        log.info("Publishing subscription created event for subscription: {}", subscription.getId());

        try {
            SubscriptionCreatedEvent event = SubscriptionCreatedEvent.newBuilder()
                    .setMetadata(createEventMetadata(subscription.getTenant().getId().toString(), subscription.getCreatedBy()))
                    .setSubscription(buildSubscriptionData(subscription))
                    .build();

            publishEvent(subscriptionCreatedTopic, subscription.getTenant().getId().toString(), event.toByteArray());

            log.info("Successfully published subscription created event for tenant: {}", subscription.getTenant().getId());
        } catch (Exception e) {
            log.error("Failed to publish subscription created event", e);
            throw e;
        }
    }

    /**
     * Publish subscription changed event
     */
    @CircuitBreaker(name = "kafka-producer", fallbackMethod = "handlePublishFailure")
    public void publishSubscriptionChangedEvent(Subscription subscription, String oldPlan, String changedBy) {
        log.info("Publishing subscription changed event for subscription: {}", subscription.getId());

        try {
            TenantSubscriptionChangedEvent event = TenantSubscriptionChangedEvent.newBuilder()
                    .setMetadata(createEventMetadata(subscription.getTenant().getId().toString(), changedBy))
                    .setSubscriptionData(TenantSubscriptionChangedEvent.SubscriptionData.newBuilder()
                            .setTenantId(subscription.getTenant().getId().toString())
                            .setOldPlan(oldPlan)
                            .setNewPlan(subscription.getPlan().name())
                            .setChangedAt(toTimestamp(LocalDateTime.now()))
                            .setEffectiveDate(toTimestamp(subscription.getStartDate()))
                            .setNextBillingDate(toTimestamp(subscription.getNextRenewalDate()))
                            .setChangedBy(changedBy != null ? changedBy : "system")
                            .setChangeReason("Plan update requested")
                            .build())
                    .build();

            publishEvent(subscriptionChangedTopic, subscription.getTenant().getId().toString(), event.toByteArray());

            log.info("Successfully published subscription changed event for tenant: {}", subscription.getTenant().getId());
        } catch (Exception e) {
            log.error("Failed to publish subscription changed event", e);
            throw e;
        }
    }

    /**
     * Publish subscription cancelled event
     */
    @CircuitBreaker(name = "kafka-producer", fallbackMethod = "handlePublishFailure")
    public void publishSubscriptionCancelledEvent(Subscription subscription, String reason) {
        log.info("Publishing subscription cancelled event for subscription: {}", subscription.getId());

        try {
            SubscriptionCancelledEvent event = SubscriptionCancelledEvent.newBuilder()
                    .setMetadata(createEventMetadata(subscription.getTenant().getId().toString(), subscription.getUpdatedBy()))
                    .setSubscriptionId(subscription.getId().toString())
                    .setTenantId(subscription.getTenant().getId().toString())
                    .setPlan(subscription.getPlan().name())
                    .setReason(reason != null ? reason : "Subscription cancelled")
                    .setCancelledAt(toTimestamp(LocalDateTime.now()))
                    .setEffectiveDate(toTimestamp(subscription.getEndDate()))
                    .build();

            publishEvent(subscriptionCancelledTopic, subscription.getTenant().getId().toString(), event.toByteArray());

            log.info("Successfully published subscription cancelled event for tenant: {}", subscription.getTenant().getId());
        } catch (Exception e) {
            log.error("Failed to publish subscription cancelled event", e);
            throw e;
        }
    }

    /**
     * Publish feature enabled event
     */
    @CircuitBreaker(name = "kafka-producer", fallbackMethod = "handlePublishFailure")
    public void publishFeatureEnabledEvent(FeatureFlag feature) {
        log.info("Publishing feature enabled event for feature: {} in tenant: {}",
                feature.getFeatureCode(), feature.getTenant().getId());

        try {
            FeatureEnabledEvent event = FeatureEnabledEvent.newBuilder()
                    .setMetadata(createEventMetadata(feature.getTenant().getId().toString(), feature.getUpdatedBy()))
                    .setTenantId(feature.getTenant().getId().toString())
                    .setFeatureCode(feature.getFeatureCode())
                    .setFeatureName(feature.getFeatureName())
                    .setCategory(feature.getCategory() != null ? feature.getCategory().name() : "")
                    .setEnabledAt(toTimestamp(feature.getLastEnabledAt() != null ?
                            feature.getLastEnabledAt() : LocalDateTime.now()))
                    .setTrialEnabled(false)
                    .setTrialDays(0)
                    .build();

            publishEvent(featureEnabledTopic, feature.getTenant().getId().toString(), event.toByteArray());

            log.info("Successfully published feature enabled event for: {}", feature.getFeatureCode());
        } catch (Exception e) {
            log.error("Failed to publish feature enabled event for: {}", feature.getFeatureCode(), e);
            throw e;
        }
    }

    /**
     * Publish feature disabled event
     */
    @CircuitBreaker(name = "kafka-producer", fallbackMethod = "handlePublishFailure")
    public void publishFeatureDisabledEvent(FeatureFlag feature) {
        log.info("Publishing feature disabled event for feature: {} in tenant: {}",
                feature.getFeatureCode(), feature.getTenant().getId());

        try {
            FeatureDisabledEvent event = FeatureDisabledEvent.newBuilder()
                    .setMetadata(createEventMetadata(feature.getTenant().getId().toString(), feature.getUpdatedBy()))
                    .setTenantId(feature.getTenant().getId().toString())
                    .setFeatureCode(feature.getFeatureCode())
                    .setReason("Feature disabled")
                    .setDisabledAt(toTimestamp(LocalDateTime.now()))
                    .build();

            publishEvent(featureDisabledTopic, feature.getTenant().getId().toString(), event.toByteArray());

            log.info("Successfully published feature disabled event for: {}", feature.getFeatureCode());
        } catch (Exception e) {
            log.error("Failed to publish feature disabled event for: {}", feature.getFeatureCode(), e);
            throw e;
        }
    }

    /**
     * Build tenant data for events
     */
    private TenantData buildTenantData(Tenant tenant) {
        TenantData.Builder builder = TenantData.newBuilder()
                .setTenantId(tenant.getId().toString())
                .setTenantCode(tenant.getTenantCode())
                .setName(tenant.getName())
                .setOrganizationType(tenant.getOrganizationType().name())
                .setStatus(tenant.getStatus().name())
                .setCreatedAt(toTimestamp(tenant.getCreatedAt()));

        // Add optional fields
        if (tenant.getDisplayName() != null) {
            builder.setDisplayName(tenant.getDisplayName());
        }
        if (tenant.getOrganizationEmail() != null) {
            builder.setOrganizationEmail(tenant.getOrganizationEmail());
        }
        if (tenant.getCountry() != null) {
            builder.setCountry(tenant.getCountry());
        }
        if (tenant.getIsolationStrategy() != null) {
            builder.setIsolationStrategy(tenant.getIsolationStrategy().name());
        }
        if (tenant.getMaxUsers() != null) {
            builder.setMaxUsers(tenant.getMaxUsers());
        }
        if (tenant.getStorageQuotaGb() != null) {
            builder.setStorageQuotaGb(tenant.getStorageQuotaGb());
        }

        return builder.build();
    }

    /**
     * Build subscription data for events
     */
    private SubscriptionData buildSubscriptionData(Subscription subscription) {
        SubscriptionData.Builder builder = SubscriptionData.newBuilder()
                .setSubscriptionId(subscription.getId().toString())
                .setTenantId(subscription.getTenant().getId().toString())
                .setPlan(subscription.getPlan().name())
                .setStatus(subscription.getSubscriptionStatus().name())
                .setStartDate(toTimestamp(subscription.getStartDate()));

        // Add optional fields
        if (subscription.getEndDate() != null) {
            builder.setEndDate(toTimestamp(subscription.getEndDate()));
        }
        if (subscription.getMonthlyPrice() != null) {
            builder.setMonthlyPrice(subscription.getMonthlyPrice().doubleValue());
        }
        if (subscription.getCurrency() != null) {
            builder.setCurrency(subscription.getCurrency());
        }
        if (subscription.getBillingCycle() != null) {
            builder.setBillingCycle(subscription.getBillingCycle().name());
        }
        if (subscription.getNextRenewalDate() != null) {
            builder.setNextRenewalDate(toTimestamp(subscription.getNextRenewalDate()));
        }
        builder.setAutoRenew(subscription.getAutoRenew() != null ? subscription.getAutoRenew() : false);

        return builder.build();
    }

    /**
     * Generic method to publish events
     */
    private void publishEvent(String topic, String key, byte[] eventData) {
        CompletableFuture<SendResult<String, byte[]>> future =
                kafkaTemplate.send(topic, key, eventData);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.debug("Event published successfully to topic: {}, key: {}, partition: {}, offset: {}",
                        topic, key,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            } else {
                log.error("Failed to publish event to topic: {}, key: {}", topic, key, ex);
                throw new RuntimeException("Failed to publish event", ex);
            }
        });
    }

    /**
     * Create event metadata
     */
    private EventMetadata createEventMetadata(String tenantId, String userId) {
        return EventMetadata.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setCorrelationId(UUID.randomUUID().toString())
                .setSourceService(applicationName)
                .setTimestamp(toTimestamp(Instant.now()))
                .setVersion("1.0")
                .setTenantId(tenantId)
                .setUserId(userId != null ? userId : "system")
                .setPriority(Priority.PRIORITY_MEDIUM)
                .setRetryCount(0)
                .build();
    }

    /**
     * Convert LocalDateTime to protobuf Timestamp
     */
    private Timestamp toTimestamp(LocalDateTime dateTime) {
        if (dateTime == null) {
            return toTimestamp(Instant.now());
        }
        Instant instant = dateTime.atZone(ZoneId.systemDefault()).toInstant();
        return toTimestamp(instant);
    }

    /**
     * Convert Instant to protobuf Timestamp
     */
    private Timestamp toTimestamp(Instant instant) {
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }

    /**
     * Fallback method for circuit breaker
     */
    public void handlePublishFailure(Exception ex) {
        log.error("Circuit breaker activated - Failed to publish event", ex);
        // In production, you might want to:
        // 1. Send to a dead letter queue
        // 2. Store in database for retry
        // 3. Send alert to monitoring system
    }
}