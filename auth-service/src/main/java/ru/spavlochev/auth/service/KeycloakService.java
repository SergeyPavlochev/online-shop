package ru.spavlochev.auth.service;

import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RolesResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import ru.spavlochev.auth.exception.AuthenticationException;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class KeycloakService {

    private static final Pattern USER_ID_PATTERN = Pattern.compile(".*/([^/]+)/?$");
    public static final String ROLE_USER = "ROLE_USER";

    private final Keycloak keycloakAdmin;
    private final RestTemplate restTemplate;
    private final JwtDecoder jwtDecoder;

    @Value("${keycloak.auth-server-url}")
    private String authServerUrl;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.app-client-id}")
    private String appClientId;

    @Value("${keycloak.app-client-secret}")
    private String appClientSecret;

    /**
     * Создает пользователя в Keycloak с ролью ROLE_USER.
     * Возвращает keycloak user id.
     */
    public UUID createUser(String email, String password, String firstName, String lastName) {
        log.info("Creating user in Keycloak: email={}", email);

        RealmResource realmResource = keycloakAdmin.realm(realm);

        UserRepresentation userRep = new UserRepresentation();
        userRep.setUsername(email);
        userRep.setEmail(email);
        userRep.setFirstName(firstName);
        userRep.setLastName(lastName);
        userRep.setEmailVerified(true);
        userRep.setEnabled(true);

        CredentialRepresentation passwordCredential = new CredentialRepresentation();
        passwordCredential.setType(CredentialRepresentation.PASSWORD);
        passwordCredential.setValue(password);
        passwordCredential.setTemporary(false);
        userRep.setCredentials(Collections.singletonList(passwordCredential));

        try (Response response = realmResource.users().create(userRep)) {
            String userId = extractResourceId(response.getLocation().getPath());
            log.info("User created in Keycloak with id: {}", userId);

            assignRoleToUser(userId, ROLE_USER);

            return UUID.fromString(userId);
        } catch (Exception e) {
            log.error("Failed to create user in Keycloak: email={}", email, e);
            throw new RuntimeException("Failed to create user in Keycloak: " + e.getMessage(), e);
        }
    }

    /**
     * Аутентифицирует пользователя через Keycloak с использованием appClient
     * и возвращает JWT токен.
     */
    public KeycloakToken authenticate(String email, String password) {
        try {
            String tokenUrl = buildTokenUrl();

            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/x-www-form-urlencoded");

            String body = buildTokenRequestBody(email, password);

            ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl,
                    new HttpEntity<>(body, headers), Map.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("Authentication failed for user: email={}", email);
                throw new AuthenticationException("Invalid email or password");
            }

            Map<String, Object> tokenResponse = response.getBody();
            String accessToken = (String) tokenResponse.get("access_token");
            String refreshToken = (String) tokenResponse.get("refresh_token");
            String idToken = (String) tokenResponse.get("id_token");

            if (accessToken == null || accessToken.isEmpty()) {
                log.warn("Authentication failed for user: email={}", email);
                throw new AuthenticationException("Invalid email or password");
            }

            if (idToken == null || idToken.isEmpty()) {
                throw new AuthenticationException("idToken is null or empty");
            }

            String userId = extractUserIdFromToken(idToken);

            log.info("User authenticated successfully: userId={}", userId);
            return new KeycloakToken(userId, accessToken, refreshToken);

        } catch (AuthenticationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Authentication error for user: email={}", email, e);
            throw new AuthenticationException("Authentication failed: " + e.getMessage(), e);
        }
    }

    /**
     * Проверяет валидность JWT токена через JWKS.
     */
    public boolean validateToken(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }

        String actualToken = token.startsWith("Bearer ") ? token.substring(7) : token;

        try {
            Jwt jwt = jwtDecoder.decode(actualToken);
            log.debug("Token validated successfully: sub={}", jwt.getSubject());
            return true;

        } catch (JwtException e) {
            log.debug("Token validation failed: {}", e.getMessage());
            return false;
        }
    }

    private RealmResource realmResource() {
        return keycloakAdmin.realm(realm);
    }

    private String buildTokenUrl() {
        return String.format("%s/realms/%s/protocol/openid-connect/token", authServerUrl, realm);
    }

    private String buildTokenRequestBody(String email, String password) {
        return String.format(
                "grant_type=%s&username=%s&password=%s&client_id=%s&client_secret=%s&scope=openid",
                OAuth2Constants.PASSWORD, email, password, appClientId, appClientSecret
        );
    }

    private void assignRoleToUser(String userId, String roleName) {
        RolesResource rolesResource = realmResource().roles();
        RoleRepresentation role;

        try {
            role = rolesResource.get(roleName).toRepresentation();
        } catch (Exception e) {
            role = createRole(rolesResource, roleName);
        }

        realmResource().users().get(userId).roles().realmLevel().add(Collections.singletonList(role));
        log.info("Role {} assigned to user: {}", roleName, userId);
    }

    private RoleRepresentation createRole(RolesResource rolesResource, String roleName) {
        RoleRepresentation newRole = new RoleRepresentation();
        newRole.setName(roleName);
        newRole.setDescription("Default user role");
        newRole.setComposite(false);
        rolesResource.create(newRole);
        log.info("Role {} created", roleName);
        return rolesResource.get(roleName).toRepresentation();
    }

    private String extractUserIdFromToken(String idToken) {
        try {
            String[] parts = idToken.split("\\.");
            if (parts.length != 3) {
                throw new AuthenticationException("Invalid token format");
            }

            String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
            Matcher matcher = Pattern.compile("\"sub\":\"([^\"]+)\"").matcher(payload);

            if (matcher.find()) {
                return matcher.group(1);
            }

            throw new AuthenticationException("Cannot extract user id from token");
        } catch (AuthenticationException e) {
            throw e;
        } catch (Exception e) {
            throw new AuthenticationException("Failed to extract user id from token", e);
        }
    }

    static String extractResourceId(String path) {
        Matcher matcher = USER_ID_PATTERN.matcher(path);
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * Registers order-service as a service account client in Keycloak.
     * Creates the client with service-accounts-enabled and assigns ROLE_INTERNAL_SERVICE.
     */
    public void registerM2MClient(String clientId, String clientSecret) {
        log.info("Registering M2M client in Keycloak: clientId={}", clientId);

        RealmResource realmResource = realmResource();

        // Check if client already exists
        List<ClientRepresentation> existingClients = realmResource.clients().findAll(clientId, null, null, 0, 100);
        if (!existingClients.isEmpty()) {
            log.info("M2M client already exists in Keycloak: clientId={}", clientId);
            return;
        }

        // Create the client representation
        ClientRepresentation clientRep = new ClientRepresentation();
        clientRep.setClientId(clientId);
        clientRep.setClientAuthenticatorType("client-secret");
        clientRep.setSecret(clientSecret);
        clientRep.setServiceAccountsEnabled(true);
        clientRep.setStandardFlowEnabled(false);
        clientRep.setDirectAccessGrantsEnabled(false);

        try (Response response = realmResource.clients().create(clientRep)) {
            if (response.getStatus() == 201) {
                log.info("M2M client created successfully: clientId={}", clientId);
            } else {
                log.warn("M2M client creation returned status: {}", response.getStatus());
            }
        }

        // Assign ROLE_INTERNAL_SERVICE client role to the service account user
        String serviceAccountId = getServiceAccountId(realmResource, clientId);
        if (serviceAccountId != null) {
            RoleRepresentation internalRole = getOrCreateInternalRole(realmResource);
            realmResource.users().get(serviceAccountId).roles().clientLevel(clientId)
                    .add(List.of(internalRole));
            log.info("ROLE_INTERNAL_SERVICE assigned to service account: {}", serviceAccountId);
        }
    }

    private String getServiceAccountId(RealmResource realmResource, String clientId) {
        List<ClientRepresentation> clients = realmResource.clients().findAll(clientId, null, null, 0, 100);
        if (clients.isEmpty()) return null;
        // The service account user has username = client-id
        List<UserRepresentation> serviceAccounts = realmResource.users()
                .search(clientId, null, null, null, null, null, null, null, null, null, null);
        return serviceAccounts.isEmpty() ? null : serviceAccounts.get(0).getId();
    }

    private RoleRepresentation getOrCreateInternalRole(RealmResource realmResource) {
        RolesResource rolesResource = realmResource.roles();
        try {
            return rolesResource.get("INTERNAL_SERVICE").toRepresentation();
        } catch (Exception e) {
            RoleRepresentation newRole = new RoleRepresentation();
            newRole.setName("INTERNAL_SERVICE");
            newRole.setDescription("Role for inter-service M2M communication");
            rolesResource.create(newRole);
            log.info("Role INTERNAL_SERVICE created");
            return rolesResource.get("INTERNAL_SERVICE").toRepresentation();
        }
    }

    public record KeycloakToken(String userId, String accessToken, String refreshToken) {
    }
}
