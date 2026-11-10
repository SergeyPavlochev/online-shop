package ru.spavlochev.auth.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import ru.spavlochev.auth.service.KeycloakService;

@Component
@RequiredArgsConstructor
@Slf4j
public class M2MClientInitializer implements ApplicationRunner {

    private final KeycloakService keycloakService;

    @Value("${keycloak.m2m-client-id:order-service-m2m}")
    private String m2mClientId;

    @Value("${keycloak.m2m-client-secret:}")
    private String m2mClientSecret;

    @Override
    public void run(ApplicationArguments args) {
        if (m2mClientSecret == null || m2mClientSecret.isBlank()) {
            log.warn("M2M client secret not configured, skipping M2M client registration");
            return;
        }
        try {
            keycloakService.registerM2MClient(m2mClientId, m2mClientSecret);
        } catch (Exception e) {
            log.error("Failed to register M2M client: {}", e.getMessage(), e);
            // Don't fail startup -- the client may already exist
        }
    }
}
