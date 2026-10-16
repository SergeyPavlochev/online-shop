package ru.spavlochev.auth.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import ru.spavlochev.auth.api.model.AuthResponse;
import ru.spavlochev.auth.api.model.LoginRequest;
import ru.spavlochev.auth.api.model.RegisterRequest;
import ru.spavlochev.auth.service.AuthService;

import java.util.Collections;

@RestController
@Tag(name = "Auth", description = "API аутентификации и авторизации")
@Slf4j
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * POST /register - Регистрация нового пользователя
     */
    @PostMapping(value = "/register")
    public ResponseEntity<Void> register(@RequestBody RegisterRequest registerRequest) {
        authService.register(
                registerRequest.email(),
                registerRequest.password(),
                registerRequest.firstName(),
                registerRequest.lastName(),
                registerRequest.phone()
        );
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * POST /login - Вход пользователя
     */
    @PostMapping(value = "/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest loginRequest) {
        AuthResponse response = authService.login(
                loginRequest.email(),
                loginRequest.password()
        );
        return ResponseEntity.ok(response);
    }

    /**
     * GET /validate - Проверка JWT токена
     */
    @GetMapping(value = "/validate")
    public ResponseEntity<Void> validate(HttpServletRequest request,
                                         @RequestHeader(value = "Authorization") String authorization) {
        boolean isValid = authService.validateToken(authorization);
        if (isValid) {
            var responseBuilder = ResponseEntity.ok();
            Collections.list(request.getHeaderNames()).forEach(headerName -> {
                Collections.list(request.getHeaders(headerName)).forEach(headerValue ->
                    responseBuilder.header(headerName, headerValue)
                );
            });
            return responseBuilder.build();
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .build();
        }
    }
}
