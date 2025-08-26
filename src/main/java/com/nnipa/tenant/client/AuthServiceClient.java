package com.nnipa.tenant.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.UUID; /**
 * Client for communicating with the auth-service.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthServiceClient {

    private final RestTemplate restTemplate;

    @Value("${services.auth.url:http://auth-service:8080}")
    private String authServiceUrl;

    /**
     * Validates a user token with the auth service.
     */
    public TokenValidationResponse validateToken(String token) {
        try {
            String url = authServiceUrl + "/api/v1/auth/validate";

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);

            HttpEntity<?> entity = new HttpEntity<>(headers);

            ResponseEntity<TokenValidationResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    TokenValidationResponse.class
            );

            return response.getBody();
        } catch (Exception e) {
            log.error("Failed to validate token", e);
            return null;
        }
    }

    /**
     * Gets user information from token.
     */
    public UserInfo getUserInfo(String token) {
        try {
            String url = authServiceUrl + "/api/v1/auth/userinfo";

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);

            HttpEntity<?> entity = new HttpEntity<>(headers);

            ResponseEntity<UserInfo> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    UserInfo.class
            );

            return response.getBody();
        } catch (Exception e) {
            log.error("Failed to get user info", e);
            return null;
        }
    }

    @lombok.Data
    public static class TokenValidationResponse {
        private boolean valid;
        private UUID userId;
        private UUID tenantId;
        private String[] roles;
    }

    @lombok.Data
    public static class UserInfo {
        private UUID userId;
        private String email;
        private String name;
        private UUID tenantId;
        private String[] roles;
    }
}
