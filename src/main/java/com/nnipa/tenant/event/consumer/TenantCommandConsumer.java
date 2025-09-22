package com.nnipa.tenant.event.consumer;

import com.google.protobuf.InvalidProtocolBufferException;
import com.nnipa.proto.tenant.CreateTenantCommand;
import com.nnipa.tenant.dto.request.CreateTenantRequest;
import com.nnipa.tenant.dto.response.TenantResponse;
import com.nnipa.tenant.entity.Tenant;
import com.nnipa.tenant.enums.IsolationStrategy;
import com.nnipa.tenant.event.publisher.TenantEventPublisher;
import com.nnipa.tenant.repository.TenantRepository;
import com.nnipa.tenant.service.TenantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Kafka consumer for tenant-related commands with idempotency protection
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantCommandConsumer {

    private final TenantService tenantService;
    private final TenantEventPublisher tenantEventPublisher;
    private final KafkaTemplate<String, byte[]> kafkaTemplate;
    private final TenantRepository tenantRepository;
    private final StringRedisTemplate redisTemplate;

    private static final String PROCESSING_KEY_PREFIX = "tenant:processing:";
    private static final String COMPLETED_KEY_PREFIX = "tenant:completed:";
    private static final Duration PROCESSING_LOCK_DURATION = Duration.ofMinutes(5);
    private static final Duration COMPLETED_CACHE_DURATION = Duration.ofHours(24);

    /**
     * Consume CreateTenantCommand from auth-service with idempotency protection
     */
    @KafkaListener(
            topics = "${kafka.topics.create-tenant-command:nnipa.commands.tenant.create}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handleCreateTenantCommand(
            @Payload byte[] message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        log.info("Received CreateTenantCommand from topic: {}, partition: {}, offset: {}, key: {}",
                topic, partition, offset, key);

        try {
            CreateTenantCommand command = CreateTenantCommand.parseFrom(message);
            String correlationId = command.getMetadata().getCorrelationId();
            CreateTenantCommand.TenantDetails details = command.getDetails();

            log.info("Processing CreateTenantCommand for organization: {} with correlationId: {}",
                    details.getName(), correlationId);

            // IDEMPOTENCY CHECK 1: Check if this command is currently being processed
            String processingKey = PROCESSING_KEY_PREFIX + correlationId;
            Boolean lockAcquired = redisTemplate.opsForValue()
                    .setIfAbsent(processingKey, "processing", PROCESSING_LOCK_DURATION);

            if (Boolean.FALSE.equals(lockAcquired)) {
                log.warn("Command with correlationId {} is already being processed, skipping", correlationId);
                acknowledgment.acknowledge();
                return;
            }

            try {
                // IDEMPOTENCY CHECK 2: Check if this command was already completed
                String completedKey = COMPLETED_KEY_PREFIX + correlationId;
                String existingTenantId = redisTemplate.opsForValue().get(completedKey);

                if (existingTenantId != null) {
                    log.info("Command with correlationId {} was already processed, tenant ID: {}",
                            correlationId, existingTenantId);

                    // Send the response again in case the original response was lost
                    publishTenantCreationResponse(
                            UUID.fromString(existingTenantId),
                            correlationId,
                            extractUserIdFromMetadata(details.getMetadataMap()),
                            "DUPLICATE"
                    );

                    acknowledgment.acknowledge();
                    return;
                }

                // IDEMPOTENCY CHECK 3: Check if tenant already exists by email
                Optional<Tenant> existingTenant = tenantRepository.findByOrganizationEmail(details.getAdminEmail());
                if (existingTenant.isPresent()) {
                    log.info("Tenant already exists with email: {}, tenant ID: {}",
                            details.getAdminEmail(), existingTenant.get().getId());

                    // Cache the result
                    redisTemplate.opsForValue().set(
                            completedKey,
                            existingTenant.get().getId().toString(),
                            COMPLETED_CACHE_DURATION
                    );

                    // Send response with existing tenant
                    publishTenantCreationResponse(
                            existingTenant.get().getId(),
                            correlationId,
                            extractUserIdFromMetadata(details.getMetadataMap()),
                            "SUCCESS"
                    );

                    acknowledgment.acknowledge();
                    return;
                }

                // Build internal request from command details
                CreateTenantRequest request = buildCreateTenantRequest(details);

                // Extract user ID from metadata
                String userId = extractUserIdFromMetadata(details.getMetadataMap());

                // Create tenant using existing service
                TenantResponse tenantResponse = tenantService.createTenant(
                        request,
                        userId != null ? userId : "system",
                        ""
                );

                // Cache the successful creation
                redisTemplate.opsForValue().set(
                        completedKey,
                        tenantResponse.getId().toString(),
                        COMPLETED_CACHE_DURATION
                );

                log.info("Successfully created tenant from command: {} with ID: {}",
                        details.getName(), tenantResponse.getId());

                // Publish events
                tenantEventPublisher.publishTenantCreatedEvent(tenantResponse.getId(), correlationId);

                // Send response back to auth-service
                publishTenantCreationResponse(
                        tenantResponse.getId(),
                        correlationId,
                        userId,
                        "SUCCESS"
                );

                acknowledgment.acknowledge();

            } finally {
                // Always remove the processing lock
                redisTemplate.delete(processingKey);
            }

        } catch (InvalidProtocolBufferException e) {
            log.error("Failed to parse CreateTenantCommand from offset: {}", offset, e);
            // Acknowledge to avoid stuck messages on parse errors
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process CreateTenantCommand from offset: {}", offset, e);
            // Don't acknowledge - let it retry after rebalance
            // But remove the processing lock to allow retry
            if (key != null) {
                redisTemplate.delete(PROCESSING_KEY_PREFIX + key);
            }
        }
    }

    private CreateTenantRequest buildCreateTenantRequest(CreateTenantCommand.TenantDetails details) {
        return CreateTenantRequest.builder()
                .tenantCode(details.getCode())
                .name(details.getName())
                .organizationEmail(details.getAdminEmail())
                .organizationType(mapOrganizationType(details.getOrganizationType()))
                .subscriptionPlan(mapSubscriptionPlan(details.getSubscriptionPlan()))
                .isolationStrategy(mapIsolationStrategy(details.getIsolationStrategy()))
                .metadata(details.getMetadataMap())
                .initialSettings(details.getSettingsMap())
                .build();
    }

    private String extractUserIdFromMetadata(java.util.Map<String, String> metadata) {
        return metadata != null ? metadata.get("user_id") : null;
    }

    private void publishTenantCreationResponse(UUID tenantId, String correlationId,
                                               String userId, String status) {
        try {
            com.nnipa.proto.tenant.TenantCreationResponseEvent response =
                    com.nnipa.proto.tenant.TenantCreationResponseEvent.newBuilder()
                            .setMetadata(com.nnipa.proto.common.EventMetadata.newBuilder()
                                    .setEventId(UUID.randomUUID().toString())
                                    .setCorrelationId(correlationId)
                                    .setTimestamp(com.google.protobuf.Timestamp.newBuilder()
                                            .setSeconds(System.currentTimeMillis() / 1000)
                                            .build())
                                    .build())
                            .setTenantId(tenantId.toString())
                            .setUserId(userId != null ? userId : "")
                            .setStatus(status)
                            .build();

            kafkaTemplate.send(
                    "nnipa.events.tenant.creation-response",
                    correlationId,
                    response.toByteArray()
            ).whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish TenantCreationResponseEvent", ex);
                } else {
                    log.info("Published TenantCreationResponse for user: {} with tenant: {}",
                            userId, tenantId);
                }
            });
        } catch (Exception e) {
            log.error("Error publishing tenant creation response", e);
        }
    }

    // Mapping helper methods
    private com.nnipa.tenant.enums.OrganizationType mapOrganizationType(String type) {
        if (type == null || type.isEmpty()) {
            return com.nnipa.tenant.enums.OrganizationType.CORPORATION;
        }
        try {
            return com.nnipa.tenant.enums.OrganizationType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown organization type: {}, defaulting to CORPORATION", type);
            return com.nnipa.tenant.enums.OrganizationType.CORPORATION;
        }
    }

    private com.nnipa.tenant.enums.SubscriptionPlan mapSubscriptionPlan(String plan) {
        if (plan == null || plan.isEmpty()) {
            return com.nnipa.tenant.enums.SubscriptionPlan.FREEMIUM;
        }
        try {
            return com.nnipa.tenant.enums.SubscriptionPlan.valueOf(plan.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown subscription plan: {}, defaulting to FREEMIUM", plan);
            return com.nnipa.tenant.enums.SubscriptionPlan.FREEMIUM;
        }
    }

    private com.nnipa.tenant.enums.IsolationStrategy mapIsolationStrategy(String strategy) {
        if (strategy == null || strategy.isEmpty()) {
            return IsolationStrategy.SHARED_SCHEMA_BASIC;
        }
        try {
            return com.nnipa.tenant.enums.IsolationStrategy.valueOf(strategy.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown isolation strategy: {}, defaulting to SHARED", strategy);
            return IsolationStrategy.SHARED_SCHEMA_BASIC;
        }
    }
}