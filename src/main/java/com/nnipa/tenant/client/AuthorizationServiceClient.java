package com.nnipa.tenant.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID; /**
 * Client for communicating with the authorization-service.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthorizationServiceClient {

    private final RestTemplate restTemplate;

    @Value("${services.authorization.url:http://authorization-service:8080}")
    private String authorizationServiceUrl;

    /**
     * Checks if a user has permission for a resource.
     */
    public boolean hasPermission(UUID userId, UUID tenantId, String resource, String action) {
        try {
            String url = authorizationServiceUrl + "/api/v1/authz/check";

            PermissionCheckRequest request = PermissionCheckRequest.builder()
                    .userId(userId)
                    .tenantId(tenantId)
                    .resource(resource)
                    .action(action)
                    .build();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<PermissionCheckRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<PermissionCheckResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    PermissionCheckResponse.class
            );

            return response.getBody() != null && response.getBody().isAllowed();
        } catch (Exception e) {
            log.error("Failed to check permission", e);
            return false;
        }
    }

    /**
     * Gets all permissions for a user in a tenant.
     */
    public UserPermissions getUserPermissions(UUID userId, UUID tenantId) {
        try {
            String url = String.format("%s/api/v1/authz/users/%s/tenants/%s/permissions",
                    authorizationServiceUrl, userId, tenantId);

            ResponseEntity<UserPermissions> response = restTemplate.getForEntity(
                    url,
                    UserPermissions.class
            );

            return response.getBody();
        } catch (Exception e) {
            log.error("Failed to get user permissions", e);
            return null;
        }
    }

    @lombok.Builder
    @lombok.Data
    private static class PermissionCheckRequest {
        private UUID userId;
        private UUID tenantId;
        private String resource;
        private String action;
    }

    @lombok.Data
    private static class PermissionCheckResponse {
        private boolean allowed;
        private String reason;
    }

    @lombok.Data
    public static class UserPermissions {
        private UUID userId;
        private UUID tenantId;
        private String[] roles;
        private Map<String, String[]> permissions; // resource -> actions
    }
}
