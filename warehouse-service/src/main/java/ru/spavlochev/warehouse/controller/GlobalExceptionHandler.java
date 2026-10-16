package ru.spavlochev.warehouse.controller;

import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.spavlochev.warehouse.openapi.dto.ErrorResponseDto;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleEntityNotFoundException(EntityNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponseDto()
                        .code(HttpStatus.NOT_FOUND.value())
                        .message(e.getMessage()));
    }

    @ExceptionHandler(EntityExistsException.class)
    public ResponseEntity<ErrorResponseDto> handleEntityExistsException(EntityExistsException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponseDto()
                        .code(HttpStatus.CONFLICT.value())
                        .message(e.getMessage()));
    }

    @ExceptionHandler(AuthenticationCredentialsNotFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleAuthenticationException(AuthenticationCredentialsNotFoundException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponseDto()
                        .code(HttpStatus.UNAUTHORIZED.value())
                        .message("Unauthorized: " + e.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponseDto> handleAccessDeniedException(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponseDto()
                        .code(HttpStatus.FORBIDDEN.value())
                        .message("Access denied: " + e.getMessage()));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponseDto> handleRuntimeException(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponseDto()
                        .code(HttpStatus.INTERNAL_SERVER_ERROR.value())
                        .message(e.getClass().getSimpleName() + " " + e.getMessage()));
    }
}
