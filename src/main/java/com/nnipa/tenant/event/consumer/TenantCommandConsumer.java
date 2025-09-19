package com.nnipa.tenant.event.consumer;

import com.google.protobuf.InvalidProtocolBufferException;
import com.nnipa.proto.tenant.CreateTenantCommand;
import com.nnipa.proto.tenant.TenantCreatedEvent;
import com.nnipa.proto.tenant.TenantCreationResponseEvent;
import com.nnipa.proto.tenant.TenantData;
import com.nnipa.proto.common.EventMetadata;
import com.nnipa.tenant.dto.request.CreateTenantRequest;
import com.nnipa.tenant.dto.response.TenantResponse;
import com.nnipa.tenant.entity.Tenant;
import com.nnipa.tenant.enums.OrganizationType;
import com.nnipa.tenant.enums.SubscriptionPlan;
import com.nnipa.tenant.enums.IsolationStrategy;
import com.nnipa.tenant.enums.TenantStatus;
import com.nnipa.tenant.event.publisher.TenantEventPublisher;
import com.nnipa.tenant.exception.TenantAlreadyExistsException;
import com.nnipa.tenant.service.TenantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Kafka consumer for tenant-related commands from other services.
 * Handles asynchronous tenant creation when synchronous gRPC calls fail.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantCommandConsumer {

    private final TenantService tenantService;
    private final TenantEventPublisher tenantEventPublisher;
    private final KafkaTemplate<String, byte[]> kafkaTemplate;

    /**
     * Consume CreateTenantCommand from auth-service when synchronous creation fails.
     * This ensures tenant creation even when gRPC communication is down.
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
            // Parse the protobuf message - using the correct proto from tenant package
            CreateTenantCommand command = CreateTenantCommand.parseFrom(message);
            String correlationId = command.getMetadata().getCorrelationId();

            // Extract details from the command structure
            CreateTenantCommand.TenantDetails details = command.getDetails();

            log.info("Processing CreateTenantCommand for organization: {} with correlationId: {}",
                    details.getName(), correlationId);

            // Check if tenant already exists (idempotency check)
            if (isTenantAlreadyProcessed(details.getAdminEmail(), correlationId)) {
                log.info("Tenant already exists for organization: {}, skipping creation",
                        details.getName());
                acknowledgment.acknowledge();
                return;
            }

            // Build internal request from command details
            CreateTenantRequest request = buildCreateTenantRequest(details);

            // Extract user ID from metadata if available
            String userId = extractUserIdFromMetadata(details.getMetadataMap());

            // Create tenant using existing service
            TenantResponse tenantResponse = tenantService.createTenant(
                    request,
                    userId != null ? userId : "system" // Use system if no user ID
            );

            log.info("Successfully created tenant from command: {} with ID: {}",
                    details.getName(), tenantResponse.getId());

            // Publish TenantCreatedEvent for other services
            publishTenantCreatedEvent(tenantResponse, command);

            // Publish a response event back to auth-service with the actual tenant ID
            if (userId != null) {
                publishTenantCreationResponse(tenantResponse, command, userId);
            }

            // Acknowledge message after successful processing
            acknowledgment.acknowledge();

        } catch (InvalidProtocolBufferException e) {
            log.error("Failed to parse CreateTenantCommand from offset: {}", offset, e);
            // Acknowledge to avoid stuck messages - the message is corrupt
            acknowledgment.acknowledge();
        } catch (TenantAlreadyExistsException e) {
            log.warn("Tenant already exists: {}", e.getMessage());
            // Acknowledge to prevent reprocessing
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process CreateTenantCommand from offset: {}", offset, e);
            // Don't acknowledge - let it retry with backoff
        }
    }

    /**
     * Extract user ID from metadata map.
     */
    private String extractUserIdFromMetadata(Map<String, String> metadata) {
        // Try different keys that might contain user ID
        if (metadata.containsKey("user_id")) {
            return metadata.get("user_id");
        }
        if (metadata.containsKey("username")) {
            return metadata.get("username");
        }
        if (metadata.containsKey("user_email")) {
            // You might need to look up user by email
            return metadata.get("user_email");
        }
        return null;
    }

    /**
     * Check if tenant was already created (idempotency).
     */
    private boolean isTenantAlreadyProcessed(String adminEmail, String correlationId) {
        try {
            // Check by email or correlation ID
            // You might want to store processed correlation IDs in Redis for idempotency
            return tenantService.existsByEmail(adminEmail);
        } catch (Exception e) {
            log.debug("Error checking if tenant exists", e);
            return false;
        }
    }

    /**
     * Build CreateTenantRequest from Protobuf TenantDetails.
     */
    private CreateTenantRequest buildCreateTenantRequest(CreateTenantCommand.TenantDetails details) {
        CreateTenantRequest.CreateTenantRequestBuilder builder = CreateTenantRequest.builder()
                .tenantCode(details.getCode())
                .name(details.getName())
                .organizationEmail(details.getAdminEmail())
                .organizationType(mapOrganizationType(details.getOrganizationType()))
                .subscriptionPlan(mapSubscriptionPlan(details.getSubscriptionPlan()))
                .isolationStrategy(mapIsolationStrategy(details.getIsolationStrategy()))
                .autoActivate(true); // Default to auto-activate for async creation

        // Add metadata if present
        if (!details.getMetadataMap().isEmpty()) {
            Map<String, String> metadata = new HashMap<>(details.getMetadataMap());
            builder.metadata(metadata);
        }

        // Add settings if present
        if (!details.getSettingsMap().isEmpty()) {
            Map<String, String> settings = new HashMap<>(details.getSettingsMap());
            builder.initialSettings(settings);
        }

        // Set billing email if different from admin email
        if (details.getBillingEmail() != null && !details.getBillingEmail().isEmpty()) {
            builder.billingEmail(details.getBillingEmail());
        }

        return builder.build();
    }

    /**
     * Publish TenantCreatedEvent for other services to consume.
     */
    private void publishTenantCreatedEvent(TenantResponse tenantResponse, CreateTenantCommand command) {
        try {
            CreateTenantCommand.TenantDetails details = command.getDetails();

            TenantData tenantData = TenantData.newBuilder()
                    .setTenantId(tenantResponse.getId().toString())
                    .setTenantCode(tenantResponse.getTenantCode())
                    .setName(tenantResponse.getName())
                    .setOrganizationType(tenantResponse.getOrganizationType().name())
                    .setStatus(tenantResponse.getStatus().name())
                    .setOrganizationEmail(details.getAdminEmail())
                    .setIsolationStrategy(details.getIsolationStrategy())
                    .setCreatedAt(com.google.protobuf.Timestamp.newBuilder()
                            .setSeconds(Instant.now().getEpochSecond())
                            .build())
                    .build();

            TenantCreatedEvent event = TenantCreatedEvent.newBuilder()
                    .setMetadata(EventMetadata.newBuilder()
                            .setEventId(UUID.randomUUID().toString())
                            .setCorrelationId(command.getMetadata().getCorrelationId())
                            .setSourceService("tenant-management-service")
                            .setEventType("TenantCreated")
                            .setTimestamp(com.google.protobuf.Timestamp.newBuilder()
                                    .setSeconds(Instant.now().getEpochSecond())
                                    .build())
                            .build())
                    .setTenant(tenantData)
                    .build();

            kafkaTemplate.send(
                    "nnipa.events.tenant.created",
                    command.getMetadata().getCorrelationId(),
                    event.toByteArray()
            );

            log.info("Published TenantCreatedEvent for tenant: {}", tenantResponse.getId());

        } catch (Exception e) {
            log.error("Failed to publish TenantCreatedEvent", e);
        }
    }

    /**
     * Publish response back to auth-service with the actual tenant ID.
     * Auth-service needs to update the user's tenant association.
     */
    private void publishTenantCreationResponse(TenantResponse tenantResponse,
                                               CreateTenantCommand command,
                                               String userId) {
        try {
            CreateTenantCommand.TenantDetails details = command.getDetails();

            // Create a custom response event for auth-service
            TenantCreationResponseEvent response = TenantCreationResponseEvent.newBuilder()
                    .setMetadata(EventMetadata.newBuilder()
                            .setEventId(UUID.randomUUID().toString())
                            .setCorrelationId(command.getMetadata().getCorrelationId())
                            .setSourceService("tenant-management-service")
                            .setEventType("TenantCreationResponse")
                            .setTimestamp(com.google.protobuf.Timestamp.newBuilder()
                                    .setSeconds(Instant.now().getEpochSecond())
                                    .build())
                            .build())
                    .setUserId(userId)
                    .setUserEmail(details.getMetadataMap().get("user_email"))
                    .setTenantId(tenantResponse.getId().toString())
                    .setTenantCode(tenantResponse.getTenantCode())
                    .setOrganizationName(details.getName())
                    .setStatus("SUCCESS")
                    .setCreatedAt(com.google.protobuf.Timestamp.newBuilder()
                            .setSeconds(Instant.now().getEpochSecond())
                            .build())
                    .build();

            // Send to a specific topic that auth-service listens to
            kafkaTemplate.send(
                    "nnipa.events.tenant.creation-response",
                    command.getMetadata().getCorrelationId(),
                    response.toByteArray()
            );

            log.info("Published TenantCreationResponse for user: {} with tenant: {}",
                    userId, tenantResponse.getId());

        } catch (Exception e) {
            log.error("Failed to publish TenantCreationResponse", e);
        }
    }

    /**
     * Map organization type from string to enum.
     */
    private OrganizationType mapOrganizationType(String type) {
        if (type == null || type.isEmpty()) {
            return OrganizationType.CORPORATION;
        }

        // Handle special case for SELF_SIGNUP
        if ("SELF_SIGNUP".equalsIgnoreCase(type)) {
            return OrganizationType.STARTUP; // Or whatever default you want for self-signup
        }

        try {
            return OrganizationType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown organization type: {}, defaulting to CORPORATION", type);
            return OrganizationType.CORPORATION;
        }
    }

    /**
     * Map subscription plan from string to enum.
     */
    private SubscriptionPlan mapSubscriptionPlan(String plan) {
        if (plan == null || plan.isEmpty()) {
            return SubscriptionPlan.FREEMIUM;
        }

        // Handle FREE -> FREEMIUM mapping if needed
        if ("FREE".equalsIgnoreCase(plan)) {
            return SubscriptionPlan.FREEMIUM;
        }

        try {
            return SubscriptionPlan.valueOf(plan.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown subscription plan: {}, defaulting to FREEMIUM", plan);
            return SubscriptionPlan.FREEMIUM;
        }
    }

    /**
     * Map isolation strategy from string to enum.
     */
    private IsolationStrategy mapIsolationStrategy(String strategy) {
        if (strategy == null || strategy.isEmpty()) {
            return IsolationStrategy.SHARED_SCHEMA_BASIC;
        }
        try {
            return IsolationStrategy.valueOf(strategy.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown isolation strategy: {}, defaulting to SHARED", strategy);
            return IsolationStrategy.SHARED_SCHEMA_BASIC;
        }
    }
}