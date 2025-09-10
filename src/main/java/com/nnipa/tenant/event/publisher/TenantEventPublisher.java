// Complete TenantEventPublisher.java
package com.nnipa.tenant.event.publisher;

import com.google.protobuf.Timestamp;
import com.nnipa.proto.common.EventMetadata;
import com.nnipa.proto.tenant.*;
import com.nnipa.proto.billing.PaymentFailedEvent;
import com.nnipa.tenant.entity.Tenant;
import com.nnipa.tenant.entity.TenantSettings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Publishes tenant-related events to Kafka topics using Protobuf serialization.
 * All events use byte[] serialization for Protobuf messages.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantEventPublisher {

    private final KafkaTemplate<String, byte[]> kafkaTemplate;

    @Value("${spring.kafka.topics.tenant-created:nnipa.events.tenant.created}")
    private String tenantCreatedTopic;

    @Value("${spring.kafka.topics.tenant-updated:nnipa.events.tenant.updated}")
    private String tenantUpdatedTopic;

    @Value("${spring.kafka.topics.tenant-deactivated:nnipa.events.tenant.deactivated}")
    private String tenantDeactivatedTopic;

    @Value("${spring.kafka.topics.tenant-migrated:nnipa.events.tenant.migrated}")
    private String tenantMigratedTopic;

    @Value("${spring.kafka.topics.tenant-subscription-changed:nnipa.events.tenant.subscription-changed}")
    private String subscriptionChangedTopic;

    @Value("${spring.kafka.topics.tenant-suspended:nnipa.events.tenant.suspended}")
    private String tenantSuspendedTopic;

    @Value("${spring.kafka.topics.tenant-reactivated:nnipa.events.tenant.reactivated}")
    private String tenantReactivatedTopic;

    @Value("${spring.kafka.topics.tenant-limit-exceeded:nnipa.events.tenant.limit-exceeded}")
    private String tenantLimitExceededTopic;

    @Value("${spring.kafka.topics.payment-failed:nnipa.events.billing.payment-failed}")
    private String paymentFailedTopic;

    @Value("${spring.application.name:tenant-management-service}")
    private String serviceName;

    /**
     * Publish tenant created event.
     */
    public void publishTenantCreatedEvent(Tenant tenant) {
        try {
            TenantCreatedEvent event = TenantCreatedEvent.newBuilder()
                    .setMetadata(createEventMetadata())
                    .setTenant(buildTenantData(tenant))
                    .build();

            String key = tenant.getId().toString();
            byte[] value = event.toByteArray();

            kafkaTemplate.send(tenantCreatedTopic, key, value)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish tenant created event for tenant: {}",
                                    tenant.getId(), ex);
                        } else {
                            log.info("Published tenant created event for tenant: {} to partition: {} at offset: {}",
                                    tenant.getId(),
                                    result.getRecordMetadata().partition(),
                                    result.getRecordMetadata().offset());
                        }
                    });

        } catch (Exception e) {
            log.error("Error publishing tenant created event", e);
        }
    }

    /**
     * Publish tenant updated event.
     */
    public void publishTenantUpdatedEvent(Tenant tenant) {
        try {
            TenantUpdatedEvent event = TenantUpdatedEvent.newBuilder()
                    .setMetadata(createEventMetadata())
                    .setTenant(buildTenantData(tenant))
                    .setUpdatedAt(Timestamp.newBuilder()
                            .setSeconds(Instant.now().getEpochSecond())
                            .build())
                    .build();

            String key = tenant.getId().toString();
            byte[] value = event.toByteArray();

            kafkaTemplate.send(tenantUpdatedTopic, key, value)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish tenant updated event", ex);
                        } else {
                            log.debug("Published tenant updated event for tenant: {}", tenant.getId());
                        }
                    });

        } catch (Exception e) {
            log.error("Error publishing tenant updated event", e);
        }
    }

    /**
     * Publish tenant deactivated event.
     */
    public void publishTenantDeactivatedEvent(Tenant tenant) {
        try {
            TenantDeactivatedEvent event = TenantDeactivatedEvent.newBuilder()
                    .setMetadata(createEventMetadata())
                    .setTenantId(tenant.getId().toString())
                    .setTenantCode(tenant.getTenantCode())
                    .setReason("Tenant deactivation requested")
                    .setDeactivatedAt(Timestamp.newBuilder()
                            .setSeconds(Instant.now().getEpochSecond())
                            .build())
                    .build();

            String key = tenant.getId().toString();
            byte[] value = event.toByteArray();

            kafkaTemplate.send(tenantDeactivatedTopic, key, value)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish tenant deactivated event", ex);
                        } else {
                            log.info("Published tenant deactivated event for tenant: {}", tenant.getId());
                        }
                    });

        } catch (Exception e) {
            log.error("Error publishing tenant deactivated event", e);
        }
    }

    /**
     * Publish tenant migrated event.
     */
    public void publishTenantMigratedEvent(Tenant tenant, String fromStrategy, String toStrategy) {
        try {
            TenantMigratedEvent event = TenantMigratedEvent.newBuilder()
                    .setMetadata(createEventMetadata())
                    .setMigration(TenantMigratedEvent.MigrationData.newBuilder()
                            .setTenantId(tenant.getId().toString())
                            .setFromStrategy(fromStrategy)
                            .setToStrategy(toStrategy)
                            .setStartedAt(Timestamp.newBuilder()
                                    .setSeconds(Instant.now().getEpochSecond())
                                    .build())
                            .setStatus("INITIATED")
                            .build())
                    .build();

            String key = tenant.getId().toString();
            byte[] value = event.toByteArray();

            kafkaTemplate.send(tenantMigratedTopic, key, value)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish tenant migrated event", ex);
                        } else {
                            log.info("Published tenant migrated event for tenant: {}", tenant.getId());
                        }
                    });

        } catch (Exception e) {
            log.error("Error publishing tenant migrated event", e);
        }
    }

    /**
     * Publish subscription changed event.
     */
    public void publishSubscriptionChangedEvent(Tenant tenant, String oldPlan, String newPlan) {
        try {
            TenantSubscriptionChangedEvent.Builder eventBuilder = TenantSubscriptionChangedEvent.newBuilder()
                    .setMetadata(createEventMetadata());

            TenantSubscriptionChangedEvent.SubscriptionData.Builder dataBuilder =
                    TenantSubscriptionChangedEvent.SubscriptionData.newBuilder()
                            .setTenantId(tenant.getId().toString())
                            .setOldPlan(oldPlan != null ? oldPlan : "")
                            .setNewPlan(newPlan != null ? newPlan : "")
                            .setChangedAt(Timestamp.newBuilder()
                                    .setSeconds(Instant.now().getEpochSecond())
                                    .build());

            if (tenant.getNextBillingDate() != null) {
                dataBuilder.setNextBillingDate(Timestamp.newBuilder()
                        .setSeconds(tenant.getNextBillingDate().getEpochSecond())
                        .build());
            }

            eventBuilder.setSubscriptionData(dataBuilder.build());
            TenantSubscriptionChangedEvent event = eventBuilder.build();

            String key = tenant.getId().toString();
            byte[] value = event.toByteArray();

            kafkaTemplate.send(subscriptionChangedTopic, key, value)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish subscription changed event", ex);
                        } else {
                            log.info("Published subscription changed event for tenant: {}", tenant.getId());
                        }
                    });

        } catch (Exception e) {
            log.error("Error publishing subscription changed event", e);
        }
    }

    /**
     * Publish tenant suspended event.
     */
    public void publishTenantSuspendedEvent(Tenant tenant, String reason) {
        try {
            TenantSuspendedEvent event = TenantSuspendedEvent.newBuilder()
                    .setMetadata(createEventMetadata())
                    .setTenantId(tenant.getId().toString())
                    .setReason(reason != null ? reason : "Administrative action")
                    .setSuspendedAt(Timestamp.newBuilder()
                            .setSeconds(Instant.now().getEpochSecond())
                            .build())
                    .build();

            String key = tenant.getId().toString();
            byte[] value = event.toByteArray();

            kafkaTemplate.send(tenantSuspendedTopic, key, value)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish tenant suspended event", ex);
                        } else {
                            log.info("Published tenant suspended event for tenant: {} - reason: {}",
                                    tenant.getId(), reason);
                        }
                    });

        } catch (Exception e) {
            log.error("Error publishing tenant suspended event", e);
        }
    }

    /**
     * Publish tenant reactivated event.
     */
    public void publishTenantReactivatedEvent(Tenant tenant, String reactivatedBy) {
        try {
            TenantReactivatedEvent event = TenantReactivatedEvent.newBuilder()
                    .setMetadata(createEventMetadata())
                    .setTenantId(tenant.getId().toString())
                    .setReactivatedAt(Timestamp.newBuilder()
                            .setSeconds(Instant.now().getEpochSecond())
                            .build())
                    .setReactivatedBy(reactivatedBy != null ? reactivatedBy : "system")
                    .build();

            String key = tenant.getId().toString();
            byte[] value = event.toByteArray();

            kafkaTemplate.send(tenantReactivatedTopic, key, value)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish tenant reactivated event", ex);
                        } else {
                            log.info("Published tenant reactivated event for tenant: {} by: {}",
                                    tenant.getId(), reactivatedBy);
                        }
                    });

        } catch (Exception e) {
            log.error("Error publishing tenant reactivated event", e);
        }
    }

    /**
     * Publish user limit exceeded event.
     */
    public void publishUserLimitExceededEvent(Tenant tenant) {
        try {
            Map<String, String> eventData = new HashMap<>();
            eventData.put("tenantId", tenant.getId().toString());
            eventData.put("tenantName", tenant.getName());
            eventData.put("currentUsers", String.valueOf(tenant.getCurrentUsers() != null ? tenant.getCurrentUsers() : 0));
            eventData.put("maxUsers", String.valueOf(tenant.getMaxUsers() != null ? tenant.getMaxUsers() : 0));
            eventData.put("subscriptionPlan", tenant.getSubscriptionPlan() != null ? tenant.getSubscriptionPlan() : "UNKNOWN");

            TenantUpdatedEvent event = TenantUpdatedEvent.newBuilder()
                    .setMetadata(EventMetadata.newBuilder()
                            .setEventId(UUID.randomUUID().toString())
                            .setTimestamp(Timestamp.newBuilder()
                                    .setSeconds(Instant.now().getEpochSecond())
                                    .build())
                            .setSourceService(serviceName)
                            .setVersion("1.0")
                            .setCorrelationId(UUID.randomUUID().toString())
                            .putHeaders("event_type", "USER_LIMIT_EXCEEDED")
                            .putHeaders("current_users", eventData.get("currentUsers"))
                            .putHeaders("max_users", eventData.get("maxUsers"))
                            .putHeaders("alert_level", "WARNING")
                            .build())
                    .setTenant(buildTenantData(tenant))
                    .setUpdatedAt(Timestamp.newBuilder()
                            .setSeconds(Instant.now().getEpochSecond())
                            .build())
                    .addChangedFields("currentUsers")
                    .build();

            String key = tenant.getId().toString();
            byte[] value = event.toByteArray();

            kafkaTemplate.send(tenantLimitExceededTopic, key, value)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish user limit exceeded event", ex);
                        } else {
                            log.warn("Published user limit exceeded event for tenant: {} (users: {}/{})",
                                    tenant.getId(),
                                    tenant.getCurrentUsers(),
                                    tenant.getMaxUsers());
                        }
                    });

        } catch (Exception e) {
            log.error("Error publishing user limit exceeded event", e);
        }
    }

    /**
     * Publish payment failed event.
     */
    public void publishPaymentFailedEvent(Tenant tenant, int failedCount) {
        try {
            PaymentFailedEvent event = PaymentFailedEvent.newBuilder()
                    .setMetadata(createEventMetadata())
                    .setTenantId(tenant.getId().toString())
                    .setTenantName(tenant.getName())
                    .setSubscriptionPlan(tenant.getSubscriptionPlan() != null ?
                            tenant.getSubscriptionPlan() : "UNKNOWN")
                    .setFailedAttemptCount(failedCount)
                    .setFailureReason("Payment processing failed")
                    .setFailedAt(Timestamp.newBuilder()
                            .setSeconds(Instant.now().getEpochSecond())
                            .build())
                    .build();

            String key = tenant.getId().toString();
            byte[] value = event.toByteArray();

            kafkaTemplate.send(paymentFailedTopic, key, value)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish payment failed event", ex);
                        } else {
                            log.warn("Published payment failed event for tenant: {} (attempt: {})",
                                    tenant.getId(), failedCount);
                        }
                    });

        } catch (Exception e) {
            log.error("Error publishing payment failed event", e);
        }
    }

    /**
     * Create common event metadata.
     */
    private EventMetadata createEventMetadata() {
        return EventMetadata.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setTimestamp(Timestamp.newBuilder()
                        .setSeconds(Instant.now().getEpochSecond())
                        .build())
                .setSourceService(serviceName)
                .setVersion("1.0")
                .setCorrelationId(UUID.randomUUID().toString())
                .build();
    }

    /**
     * Build tenant data for events.
     */
    private TenantCreatedEvent.TenantData buildTenantData(Tenant tenant) {
        TenantCreatedEvent.TenantData.Builder builder = TenantCreatedEvent.TenantData.newBuilder()
                .setTenantId(tenant.getId().toString())
                .setTenantCode(tenant.getTenantCode() != null ? tenant.getTenantCode() : "")
                .setName(tenant.getName() != null ? tenant.getName() : "")
                .setStatus(tenant.getStatus() != null ? tenant.getStatus().name() : "UNKNOWN");

        if (tenant.getOrganizationType() != null) {
            builder.setOrganizationType(tenant.getOrganizationType().name());
        }

        if (tenant.getSubscriptionPlan() != null) {
            builder.setSubscriptionPlan(tenant.getSubscriptionPlan());
        }

        if (tenant.getIsolationStrategy() != null) {
            builder.setIsolationStrategy(tenant.getIsolationStrategy().name());
        }

        if (tenant.getCreatedAt() != null) {
            builder.setCreatedAt(Timestamp.newBuilder()
                    .setSeconds(tenant.getCreatedAt().getEpochSecond())
                    .build());
        }

        if (tenant.getMaxUsers() != null) {
            builder.setMaxUsers(tenant.getMaxUsers());
        }

        // Convert Map<String, Object> to Map<String, String> for Protobuf
        if (tenant.getMetadata() != null) {
            Map<String, String> stringMetadata = new HashMap<>();
            tenant.getMetadata().forEach((key, value) ->
                    stringMetadata.put(key, value != null ? value.toString() : ""));
            builder.putAllMetadata(stringMetadata);
        }

        // Convert TenantSettings individual properties to Map<String, String>
        if (tenant.getSettings() != null) {
            Map<String, String> settingsMap = convertTenantSettingsToMap(tenant.getSettings());
            builder.putAllSettings(settingsMap);
        }

        // Feature flags
        if (tenant.getFeatureFlags() != null) {
            tenant.getFeatureFlags().forEach(builder::putFeatureFlags);
        }

        return builder.build();
    }

    private Map<String, String> convertTenantSettingsToMap(TenantSettings settings) {
        Map<String, String> settingsMap = new HashMap<>();

        // Add individual settings
        if (settings.getDefaultLanguage() != null) {
            settingsMap.put("defaultLanguage", settings.getDefaultLanguage());
        }
        if (settings.getDateFormat() != null) {
            settingsMap.put("dateFormat", settings.getDateFormat());
        }
        if (settings.getTimeFormat() != null) {
            settingsMap.put("timeFormat", settings.getTimeFormat());
        }
        if (settings.getTimezone() != null) {
            settingsMap.put("timezone", settings.getTimezone());
        }
        if (settings.getTheme() != null) {
            settingsMap.put("theme", settings.getTheme());
        }
        // Add other relevant settings as needed

        return settingsMap;
    }
}