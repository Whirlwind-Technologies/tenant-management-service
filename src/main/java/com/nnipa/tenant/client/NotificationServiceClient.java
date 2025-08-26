package com.nnipa.tenant.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

/**
 * Client for communicating with the notification-service.
 * Replaces the internal NotificationService implementation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationServiceClient {

    private final RestTemplate restTemplate;

    @Value("${services.notification.url:http://notification-service:8080}")
    private String notificationServiceUrl;

    /**
     * Sends a notification request to the notification service.
     */
    public void sendNotification(UUID tenantId, NotificationType type, Map<String, Object> payload) {
        try {
            String url = notificationServiceUrl + "/api/v1/notifications";

            NotificationRequest request = NotificationRequest.builder()
                    .tenantId(tenantId)
                    .type(type)
                    .payload(payload)
                    .build();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Service-Name", "tenant-management-service");

            HttpEntity<NotificationRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<Void> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    Void.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                log.debug("Notification sent successfully for tenant: {}", tenantId);
            }
        } catch (Exception e) {
            log.error("Failed to send notification for tenant: {}", tenantId, e);
            // Don't fail the main operation if notification fails
        }
    }

    public enum NotificationType {
        SUBSCRIPTION_CREATED,
        SUBSCRIPTION_CHANGED,
        SUBSCRIPTION_CANCELLED,
        SUBSCRIPTION_RENEWED,
        TRIAL_ENDING,
        USAGE_ALERT,
        TENANT_ACTIVATED,
        TENANT_SUSPENDED,
        FEATURE_ENABLED,
        FEATURE_DISABLED
    }

    @lombok.Builder
    @lombok.Data
    private static class NotificationRequest {
        private UUID tenantId;
        private NotificationType type;
        private Map<String, Object> payload;
    }
}

