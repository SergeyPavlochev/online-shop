package ru.spavlochev.notification.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;
import ru.spavlochev.notification.config.UserContext;
import ru.spavlochev.notification.openapi.api.NotificationsApi;
import ru.spavlochev.notification.openapi.dto.NotificationDto;
import ru.spavlochev.notification.service.NotificationService;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class NotificationsRestController implements NotificationsApi {

    private final NotificationService notificationService;

    @Override
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<NotificationDto>> getUserNotifications(Integer limit) {
        String userId = UserContext.getCurrentUserId();
        var notifications = notificationService.getNotifications(java.util.UUID.fromString(userId), limit);
        return ResponseEntity.ok(notifications);
    }
}
