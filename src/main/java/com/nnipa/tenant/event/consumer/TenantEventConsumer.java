package com.nnipa.tenant.event.consumer;

import com.google.protobuf.InvalidProtocolBufferException;
import com.nnipa.proto.billing.BillingEvent;
import com.nnipa.proto.user.UserCreatedEvent;
import com.nnipa.tenant.service.TenantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Consumes events from other services that affect tenant state.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantEventConsumer {

    private final TenantService tenantService;

    /**
     * Handle user created events to update tenant user count.
     */
    @KafkaListener(
            topics = "nnipa.events.user.created",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleUserCreated(
            @Payload byte[] payload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        try {
            UserCreatedEvent event = UserCreatedEvent.parseFrom(payload);
            UUID tenantId = UUID.fromString(event.getTenantId());

            log.info("Received user created event for tenant: {}", tenantId);

            // Update tenant user count
            tenantService.incrementUserCount(tenantId);

            acknowledgment.acknowledge();

        } catch (InvalidProtocolBufferException e) {
            log.error("Failed to parse user created event", e);
            acknowledgment.acknowledge(); // Acknowledge to avoid reprocessing
        } catch (Exception e) {
            log.error("Error handling user created event", e);
            // Don't acknowledge - let it retry
        }
    }

    /**
     * Handle billing events that might affect tenant status.
     */
    @KafkaListener(
            topics = "nnipa.events.billing.payment-failed",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handlePaymentFailed(
            @Payload byte[] payload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            Acknowledgment acknowledgment) {

        try {
            BillingEvent event = BillingEvent.parseFrom(payload);
            UUID tenantId = UUID.fromString(event.getTenantId());

            log.warn("Payment failed for tenant: {}", tenantId);

            // Suspend tenant if payment fails
            tenantService.suspendTenant(tenantId, "Payment failed");

            acknowledgment.acknowledge();

        } catch (InvalidProtocolBufferException e) {
            log.error("Failed to parse billing event", e);
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Error handling billing event", e);
            // Don't acknowledge - let it retry
        }
    }
}