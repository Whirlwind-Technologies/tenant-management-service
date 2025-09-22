package com.nnipa.tenant.event.consumer;

import com.google.protobuf.InvalidProtocolBufferException;
import com.nnipa.proto.command.CreateTenantCommand;
import com.nnipa.proto.command.ProvisionResourcesCommand;
import com.nnipa.proto.command.AssignRoleCommand;
import com.nnipa.tenant.dto.request.CreateTenantRequest;
import com.nnipa.tenant.enums.OrganizationType;
import com.nnipa.tenant.enums.SubscriptionPlan;
import com.nnipa.tenant.enums.BillingCycle;
import com.nnipa.tenant.service.TenantService;
import com.nnipa.tenant.service.SubscriptionService;
import com.nnipa.tenant.service.FeatureFlagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Consumer for tenant-related events and commands from other services
 * Uses conditional registration to handle missing topics gracefully
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "kafka.consumers.enabled", havingValue = "true", matchIfMissing = false)
public class TenantEventConsumer {

    private final TenantService tenantService;
    private final SubscriptionService subscriptionService;
    private final FeatureFlagService featureFlagService;

    /**
     * Consume create tenant command (from self-signup or admin)
     * Only enabled when the specific topic consumer is enabled
     */
    @KafkaListener(
            topics = "${kafka.topics.create-tenant-command}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @ConditionalOnProperty(name = "kafka.consumers.tenant-commands.enabled", havingValue = "true")
    public void handleCreateTenantCommand(
            @Payload byte[] message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        log.info("Received create tenant command from topic: {}, partition: {}, offset: {}, key: {}",
                topic, partition, offset, key);

        try {
            CreateTenantCommand command = CreateTenantCommand.parseFrom(message);
            log.info("Processing create tenant command for organization: {}", command.getOrganizationName());

            CreateTenantRequest request = buildCreateTenantRequest(command);
            tenantService.createTenant(request, command.getUserId(), "");

            log.info("Successfully created tenant from command: {}", command.getOrganizationName());
            acknowledgment.acknowledge();

        } catch (InvalidProtocolBufferException e) {
            log.error("Failed to parse CreateTenantCommand from offset: {}", offset, e);
        } catch (Exception e) {
            log.error("Failed to process create tenant command from offset: {}", offset, e);
        }
    }

    /**
     * Consume provision tenant command
     */
    @KafkaListener(
            topics = "${kafka.topics.provision-tenant-command}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @ConditionalOnProperty(name = "kafka.consumers.tenant-commands.enabled", havingValue = "true")
    public void handleProvisionTenantCommand(
            @Payload byte[] message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        log.info("Received provision tenant command from topic: {}, key: {}", topic, key);

        try {
            ProvisionResourcesCommand command = ProvisionResourcesCommand.parseFrom(message);
            log.info("Processing provision command for tenant: {}", command.getTenantId());

            UUID tenantId = UUID.fromString(command.getTenantId());
            processResourceProvisioning(command);
            tenantService.activateTenant(tenantId, "system");

            log.info("Successfully processed provision command for tenant: {}", command.getTenantId());
            acknowledgment.acknowledge();

        } catch (InvalidProtocolBufferException e) {
            log.error("Failed to parse ProvisionResourcesCommand", e);
        } catch (IllegalArgumentException e) {
            log.error("Invalid tenant ID in provision command", e);
            acknowledgment.acknowledge(); // Don't retry invalid data
        } catch (Exception e) {
            log.error("Failed to process provision tenant command", e);
        }
    }

    /**
     * User lifecycle event consumers - only enabled when user service integration is active
     */
    @KafkaListener(
            topics = "nnipa.events.user.created",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @ConditionalOnProperty(name = "kafka.consumers.user-events.enabled", havingValue = "true")
    public void handleUserCreatedEvent(ConsumerRecord<String, byte[]> record, Acknowledgment acknowledgment) {
        log.debug("Received user created event, key: {}", record.key());
        try {
            if (record.key() != null) {
                UUID tenantId = UUID.fromString(record.key());
                checkTenantUserLimits(tenantId);
            }
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process user created event", e);
            acknowledgment.acknowledge();
        }
    }

    @KafkaListener(
            topics = "nnipa.events.user.deleted",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @ConditionalOnProperty(name = "kafka.consumers.user-events.enabled", havingValue = "true")
    public void handleUserDeletedEvent(ConsumerRecord<String, byte[]> record, Acknowledgment acknowledgment) {
        log.debug("Received user deleted event, key: {}", record.key());
        try {
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process user deleted event", e);
            acknowledgment.acknowledge();
        }
    }

    /**
     * Payment event consumers - only enabled when payment service integration is active
     */
    @KafkaListener(
            topics = "nnipa.events.payment.success",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @ConditionalOnProperty(name = "kafka.consumers.payment-events.enabled", havingValue = "true")
    public void handlePaymentSuccessEvent(ConsumerRecord<String, byte[]> record, Acknowledgment acknowledgment) {
        log.info("Received payment success event");
        try {
            // Process payment success - activate subscription, clear failed payment count
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process payment success event", e);
            acknowledgment.acknowledge();
        }
    }

    @KafkaListener(
            topics = "nnipa.events.payment.failed",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @ConditionalOnProperty(name = "kafka.consumers.payment-events.enabled", havingValue = "true")
    public void handlePaymentFailedEvent(ConsumerRecord<String, byte[]> record, Acknowledgment acknowledgment) {
        log.warn("Received payment failed event");
        try {
            // Process payment failure - increment failed count, suspend if threshold reached
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process payment failed event", e);
            acknowledgment.acknowledge();
        }
    }

    /**
     * Auth event consumers - only enabled when auth service integration is active
     */
    @KafkaListener(
            topics = "nnipa.events.auth.login",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @ConditionalOnProperty(name = "kafka.consumers.auth-events.enabled", havingValue = "true")
    public void handleUserLoginEvent(ConsumerRecord<String, byte[]> record, Acknowledgment acknowledgment) {
        log.debug("Received user login event for activity tracking");
        try {
            if (record.key() != null) {
                updateTenantActivity(record.key());
            }
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process user login event", e);
            acknowledgment.acknowledge();
        }
    }

    // Helper methods (keeping existing implementation)
    private CreateTenantRequest buildCreateTenantRequest(CreateTenantCommand command) {
        CreateTenantRequest request = CreateTenantRequest.builder()
                .tenantCode(generateTenantCode(command.getOrganizationName(), command.getOrganizationType()))
                .name(command.getOrganizationName())
                .displayName(command.getOrganizationName())
                .organizationType(mapOrganizationType(command.getOrganizationType()))
                .organizationEmail(command.getOrganizationEmail())
                .organizationPhone(command.getOrganizationPhone())
                .subscriptionPlan(mapSubscriptionPlan(command.getSubscriptionPlan()))
                .billingCycle(BillingCycle.MONTHLY)
                .autoActivate(command.getAutoActivate())
                .initialSettings(new HashMap<>(command.getAdditionalSettingsMap()))
                .build();

        if (request.getSubscriptionPlan() == SubscriptionPlan.TRIAL) {
            request.setTrialEndsAt(LocalDateTime.now().plusDays(30));
        }
        return request;
    }

    private String generateTenantCode(String organizationName, String organizationType) {
        String cleanName = organizationName.toUpperCase()
                .replaceAll("[^A-Z0-9]", "")
                .substring(0, Math.min(organizationName.length(), 10));
        String typePrefix = getTypePrefix(organizationType);
        String suffix = String.valueOf(System.currentTimeMillis()).substring(8);
        return String.format("%s-%s-%s", typePrefix, cleanName, suffix);
    }

    private String getTypePrefix(String type) {
        if (type == null) return "ORG";
        return switch (type.toUpperCase()) {
            case "GOVERNMENT" -> "GOV";
            case "CORPORATION" -> "CORP";
            case "ACADEMIC_INSTITUTION" -> "ACAD";
            case "NON_PROFIT" -> "NPO";
            case "HEALTHCARE" -> "HC";
            case "FINANCIAL_INSTITUTION" -> "FIN";
            case "STARTUP" -> "SU";
            case "INDIVIDUAL" -> "IND";
            default -> "ORG";
        };
    }

    private OrganizationType mapOrganizationType(String type) {
        if (type == null || type.isEmpty()) {
            return OrganizationType.CORPORATION;
        }
        try {
            return OrganizationType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown organization type: {}, defaulting to CORPORATION", type);
            return OrganizationType.CORPORATION;
        }
    }

    private SubscriptionPlan mapSubscriptionPlan(String plan) {
        if (plan == null || plan.isEmpty()) {
            return SubscriptionPlan.TRIAL;
        }
        try {
            return SubscriptionPlan.valueOf(plan.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown subscription plan: {}, defaulting to TRIAL", plan);
            return SubscriptionPlan.TRIAL;
        }
    }

    private void processResourceProvisioning(ProvisionResourcesCommand command) {
        log.info("Provisioning resources for tenant: {}", command.getTenantId());
        for (String resource : command.getResourcesList()) {
            switch (resource.toLowerCase()) {
                case "database" -> log.info("Would provision database for tenant: {}", command.getTenantId());
                case "storage" -> log.info("Would provision storage for tenant: {}", command.getTenantId());
                case "api_keys" -> log.info("Would generate API keys for tenant: {}", command.getTenantId());
                case "default_data" -> log.info("Would setup default data for tenant: {}", command.getTenantId());
                case "integrations" -> log.info("Would setup integrations for tenant: {}", command.getTenantId());
                default -> log.warn("Unknown resource type: {}", resource);
            }
        }
    }

    private void checkTenantUserLimits(UUID tenantId) {
        try {
            var tenant = tenantService.getTenant(tenantId);
            if (tenant.getMaxUsers() != null) {
                log.debug("Checking user limits for tenant: {}", tenantId);
            }
        } catch (Exception e) {
            log.error("Failed to check user limits for tenant: {}", tenantId, e);
        }
    }

    private void updateTenantActivity(String identifier) {
        log.debug("Updating activity for: {}", identifier);
    }
}