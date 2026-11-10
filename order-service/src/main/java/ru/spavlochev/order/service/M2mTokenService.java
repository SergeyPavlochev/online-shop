package ru.spavlochev.order.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Map;

/**
 * Сервис для получения M2M OAuth2 токена через client_credentials grant.
 * Кэширует токен с TTL для избежания избыточных запросов к Keycloak.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class M2mTokenService {

    private static final int TOKEN_BUFFER_SECONDS = 60;

    private final RestClient keycloakRestClient;

    @Value("${KEYCLOAK_TOKEN_URI}")
    private String tokenUri;
    @Value("${spring.security.oauth2.client.registration.order-service-m2m.client-id}")
    private String clientId;
    @Value("${spring.security.oauth2.client.registration.order-service-m2m.client-secret:}")
    private String clientSecret;
    private String cachedToken;
    private Instant tokenExpiry;

    @PostConstruct
    public void init() {
        refreshToken();
    }

    /**
     * Возвращает текущий действующий токен. При истечении — автоматически обновляет.
     */
    public String getToken() {
        if (cachedToken == null || Instant.now().isAfter(tokenExpiry.minusSeconds(TOKEN_BUFFER_SECONDS))) {
            refreshToken();
        }
        return cachedToken;
    }

    private void refreshToken() {
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "client_credentials");
            form.add("client_id", clientId);
            if (!clientSecret.isBlank()) {
                form.add("client_secret", clientSecret);
            }

            ResponseEntity<Map> response = keycloakRestClient.post()
                    .uri(tokenUri)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toEntity(Map.class);

            if (response.getStatusCode().is4xxClientError()) {
                throw new RuntimeException("M2M token request failed: " + response.getStatusCode());
            }
            if (response.getStatusCode().is5xxServerError()) {
                throw new RuntimeException("M2M token request failed: " + response.getStatusCode());
            }

            Map<String, Object> body = response.getBody();
            if (body == null) {
                throw new RuntimeException("M2M token response is null");
            }

            cachedToken = (String) body.get("access_token");
            int expiresIn = Integer.parseInt(String.valueOf(body.getOrDefault("expires_in", 60)));
            tokenExpiry = Instant.now().plusSeconds(expiresIn);

            log.info("M2M token refreshed, expires in {}s", expiresIn);
        } catch (ResourceAccessException e) {
            log.error("Failed to refresh M2M token (network error): {}", e.getMessage(), e);
            if (cachedToken == null) {
                throw new RuntimeException("Initial M2M token acquisition failed", e);
            }
        } catch (Exception e) {
            log.error("Failed to refresh M2M token: {}", e.getMessage(), e);
            if (cachedToken == null) {
                throw new RuntimeException("Initial M2M token acquisition failed", e);
            }
        }
    }
}
