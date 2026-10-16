package ru.spavlochev.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.spavlochev.auth.api.model.AuthResponse;
import ru.spavlochev.auth.entity.AppUser;
import ru.spavlochev.auth.exception.AuthenticationException;
import ru.spavlochev.auth.exception.UserAlreadyExistsException;
import ru.spavlochev.auth.repository.AppUserRepository;

import java.util.Map;
import java.util.UUID;


@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private static final String EVENT_TYPE_USER_REGISTERED = "UserRegistered";
    public static final String AGGREGATE_TYPE_USER = "User";

    private final AppUserRepository appUserRepository;
    private final KeycloakService keycloakService;
    private final OutboxService outboxService;

    /**
     * Регистрация нового пользователя.
     * 1. Проверяет уникальность email в локальной БД
     * 2. Создает пользователя в Keycloak
     * 3. Сохраняет данные в локальную БД
     * 4. Записывает событие в outbox_events
     */
    @Transactional
    public void register(String email, String password,
                                 String firstName, String lastName,
                                 String phone) {
        // 1. Проверяем, нет ли уже пользователя с таким email
        if (appUserRepository.existsByEmail(email)) {
            log.warn("Registration failed: user already exists with email={}", email);
            throw new UserAlreadyExistsException("User with email " + email + " already exists");
        }

        // 2. Создаем пользователя в Keycloak
        UUID userId;
        try {
            userId = keycloakService.createUser(email, password, firstName, lastName);
            log.info("User created in Keycloak: userId={}", userId);
        } catch (Exception e) {
            log.error("Failed to create user in Keycloak: email={}", email, e);
            throw new RuntimeException("Failed to create user in Keycloak: " + e.getMessage(), e);
        }

        // 3. Сохраняем пользователя в локальную БД
        var appUser = appUserRepository.save(AppUser.builder()
                .id(userId)
                .firstName(firstName)
                .lastName(lastName)
                .email(email)
                .phone(phone)
                .build());
        log.info("User saved to local DB: id={}, email={}", appUser.getId(), email);

        // 4. Записываем событие в outbox
        try {
            var eventId = UUID.randomUUID();
            outboxService.saveEvent(eventId, EVENT_TYPE_USER_REGISTERED, appUser.getId(), AGGREGATE_TYPE_USER,
                    Map.of("userId", appUser.getId(),
                            "email", appUser.getEmail()));
            log.info("Outbox event created: eventId={}", eventId);
        } catch (Exception e) {
            log.error("Failed to create outbox event for user: email={}", email, e);
            // Не блокируем регистрацию из-за ошибки outbox - событие будет обработано планировщиком
        }

        log.info("User registered successfully: id={}, email={}", appUser.getId(), email);
    }

    /**
     * Вход пользователя.
     * 1. Аутентифицирует через Keycloak
     * 2. Возвращает userId + JWT
     */
    public AuthResponse login(String email, String password) {
        // Аутентифицируем через Keycloak
        KeycloakService.KeycloakToken token;
        try {
            token = keycloakService.authenticate(email, password);
        } catch (AuthenticationException e) {
            log.warn("Login failed for user: email={}", email);
            throw e;
        }

        // Получаем пользователя из локальной БД
        AppUser appUser = appUserRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.error("User not found in local DB after successful Keycloak auth: email={}", email);
                    return new RuntimeException("User not found in local database");
                });

        log.info("User logged in successfully: id={}, email={}", appUser.getId(), email);

        // Формируем ответ
        return new AuthResponse(token.accessToken());
    }

    /**
     * Проверка валидности JWT токена.
     */
    public boolean validateToken(String token) {
        log.debug("Validating token for API Gateway");
        return keycloakService.validateToken(token);
    }
}
