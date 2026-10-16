package ru.spavlochev.auth.config;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
@Slf4j
public class KeycloakConfig {

    @Value("${keycloak.auth-server-url}")
    private String authServerUrl;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.admin-client-id}")
    private String adminClientId;

    @Value("${keycloak.admin-client-secret}")
    private String adminClientSecret;

    @Bean
    public Keycloak keycloak() {
        return KeycloakBuilder.builder()
                .serverUrl(authServerUrl)
                .realm(realm)
                .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                .clientId(adminClientId)
                .clientSecret(adminClientSecret)
                .build();
    }

    @Bean
    public ClientHttpRequestInterceptor traceIdInterceptor() {
        return (org.springframework.http.HttpRequest httpRequest, byte[] body, ClientHttpRequestExecution execution) -> {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String traceId = request.getHeader("X-Correlation-Id");
                if (traceId != null && !traceId.isBlank()) {
                    httpRequest.getHeaders().set("X-Correlation-Id", traceId);
                }
            }
            return execution.execute(httpRequest, body);
        };
    }

    @Bean
    public RestTemplate restTemplate(ClientHttpRequestInterceptor traceIdInterceptor) {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setInterceptors(java.util.List.of(traceIdInterceptor()));
        return restTemplate;
    }
}
