package ru.spavlochev.notification.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.spavlochev.notification.entity.Notification;
import ru.spavlochev.notification.openapi.dto.NotificationDto;
import ru.spavlochev.notification.repository.NotificationRepository;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;

    @Transactional
    public void createOrderPaidNotification(UUID userId, UUID orderId, String amount) {
        log.info("Creating ORDER_PAID notification for userId={}, orderId={}", userId, orderId);

        Notification notification = notificationRepository.save(Notification.builder()
                .userId(userId)
                .type(Notification.NotificationType.ORDER_PAID)
                .orderId(orderId)
                .amount(amount)
                .message("Ваш заказ успешно оплачен. Сумма: " + amount)
                .build());

        logNotificationCreated(notification);
    }

    @Transactional
    public void createOrderFailedNotification(UUID userId, UUID orderId, String amount, String reason) {
        log.info("Creating ORDER_FAILED notification for userId={}, orderId={}, reason={}",
                userId, orderId, reason);

        Notification notification = notificationRepository.save(Notification.builder()
                .userId(userId)
                .type(Notification.NotificationType.ORDER_FAILED)
                .orderId(orderId)
                .amount(amount)
                .message("Оплата заказа не удалась. Причина: " + reason)
                .build());

        logNotificationCreated(notification);
    }

    @Transactional
    public void createOrderConfirmedNotification(UUID userId, UUID orderId, String amount) {
        log.info("Creating ORDER_CONFIRMED notification for userId={}, orderId={}", userId, orderId);

        Notification notification = notificationRepository.save(Notification.builder()
                .userId(userId)
                .type(Notification.NotificationType.ORDER_CONFIRMED)
                .orderId(orderId)
                .amount(amount)
                .message("Ваш заказ подтвержден. Сумма: " + amount)
                .build());

        logNotificationCreated(notification);
    }

    @Transactional
    public void createOrderCancelledNotification(UUID userId, UUID orderId, String amount, String reason) {
        log.info("Creating ORDER_CANCELLED notification for userId={}, orderId={}, reason={}",
                userId, orderId, reason);

        Notification notification = notificationRepository.save(Notification.builder()
                .userId(userId)
                .type(Notification.NotificationType.ORDER_CANCELLED)
                .orderId(orderId)
                .amount(amount)
                .message("Заказ отменен. Причина: " + reason)
                .build());

        logNotificationCreated(notification);
    }

    @Transactional
    public void createOrderPickingStartedNotification(UUID userId, UUID orderId) {
        log.info("Creating ORDER_PICKING_STARTED notification for userId={}, orderId={}", userId, orderId);

        Notification notification = notificationRepository.save(Notification.builder()
                .userId(userId)
                .type(Notification.NotificationType.ORDER_PICKING_STARTED)
                .orderId(orderId)
                .amount("")
                .message("Ваш заказ передан на склад для подбора")
                .build());

        logNotificationCreated(notification);
    }

    @Transactional
    public void createOrderPickingCompletedNotification(UUID userId, UUID orderId, String trackingNumber) {
        log.info("Creating ORDER_PICKING_COMPLETED notification for userId={}, orderId={}, tracking={}",
                userId, orderId, trackingNumber);

        Notification notification = notificationRepository.save(Notification.builder()
                .userId(userId)
                .type(Notification.NotificationType.ORDER_PICKING_COMPLETED)
                .orderId(orderId)
                .amount("")
                .message("Товар собран на складе. Трек-номер: " + trackingNumber)
                .build());

        logNotificationCreated(notification);
    }

    @Transactional
    public void createOrderPickingFailedNotification(UUID userId, UUID orderId, String reason) {
        log.info("Creating ORDER_PICKING_FAILED notification for userId={}, orderId={}, reason={}",
                userId, orderId, reason);

        Notification notification = notificationRepository.save(Notification.builder()
                .userId(userId)
                .type(Notification.NotificationType.ORDER_PICKING_FAILED)
                .orderId(orderId)
                .amount("")
                .message("Не удалось собрать заказ на складе. Причина: " + reason)
                .build());

        logNotificationCreated(notification);
    }

    @Transactional
    public void createOrderDeliveryStartedNotification(UUID userId, UUID orderId, String trackingNumber) {
        log.info("Creating ORDER_DELIVERY_STARTED notification for userId={}, orderId={}, tracking={}",
                userId, orderId, trackingNumber);

        Notification notification = notificationRepository.save(Notification.builder()
                .userId(userId)
                .type(Notification.NotificationType.ORDER_DELIVERY_STARTED)
                .orderId(orderId)
                .amount("")
                .message("Заказ передан в доставку. Трек-номер: " + trackingNumber)
                .build());

        logNotificationCreated(notification);
    }

    @Transactional
    public void createOrderDeliveredNotification(UUID userId, UUID orderId, String trackingNumber) {
        log.info("Creating ORDER_DELIVERED notification for userId={}, orderId={}, tracking={}",
                userId, orderId, trackingNumber);

        Notification notification = notificationRepository.save(Notification.builder()
                .userId(userId)
                .type(Notification.NotificationType.ORDER_DELIVERED)
                .orderId(orderId)
                .amount("")
                .message("Заказ успешно доставлен! Трек-номер: " + trackingNumber)
                .build());

        logNotificationCreated(notification);
    }

    @Transactional
    public void createOrderDeliveryFailedNotification(UUID userId, UUID orderId, String reason) {
        log.info("Creating ORDER_DELIVERY_FAILED notification for userId={}, orderId={}, reason={}",
                userId, orderId, reason);

        Notification notification = notificationRepository.save(Notification.builder()
                .userId(userId)
                .type(Notification.NotificationType.ORDER_DELIVERY_FAILED)
                .orderId(orderId)
                .amount("")
                .message("Не удалось доставить заказ. Причина: " + reason)
                .build());

        logNotificationCreated(notification);
    }

    @Transactional
    public void createOrderPaymentRefundedNotification(UUID userId, UUID orderId, String amount) {
        log.info("Creating ORDER_PAYMENT_REFUNDED notification for userId={}, orderId={}, amount={}",
                userId, orderId, amount);

        Notification notification = notificationRepository.save(Notification.builder()
                .userId(userId)
                .type(Notification.NotificationType.ORDER_PAYMENT_REFUNDED)
                .orderId(orderId)
                .amount(amount)
                .message("Денежные средства возвращены на счёт. Сумма: " + amount)
                .build());

        logNotificationCreated(notification);
    }

    private void logNotificationCreated(Notification notification) {
        log.info("Notification created: id={}, userId={}, type={}",
                notification.getId(), notification.getUserId(), notification.getType());
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> getNotifications(UUID userId, int limit) {
        log.info("Getting notifications for userId={}, limit={}", userId, limit);
        var notifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, limit))
                .stream()
                .map(notif -> new NotificationDto()
                        .id(notif.getId())
                        .userId(notif.getUserId())
                        .type(NotificationDto.TypeEnum.valueOf(notif.getType().name()))
                        .orderId(notif.getOrderId())
                        .amount(notif.getAmount())
                        .message(notif.getMessage()))
                .toList();

        if (notifications.isEmpty()) {
            throw new EntityNotFoundException("Уведомления пользователя не найдены");
        }
        return notifications;
    }
}
