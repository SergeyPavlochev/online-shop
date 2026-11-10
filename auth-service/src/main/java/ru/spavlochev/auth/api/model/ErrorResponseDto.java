package ru.spavlochev.auth.api.model;

/**
 * Стандартный формат ответа при возникновении ошибки.
 * Унифицирован с другими сервисами проекта.
 */
public record ErrorResponseDto(int code, String message) {}
