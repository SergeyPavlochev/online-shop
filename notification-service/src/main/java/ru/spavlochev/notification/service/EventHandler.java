package ru.spavlochev.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import ru.spavlochev.notification.entity.ProcessedEvent;
import ru.spavlochev.notification.repository.ProcessedEventRepository;
import ru.spavlochev.onlineshop.events.v1.EventEnvelope;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventHandler {

    private final NotificationService notificationService;
    private final ProcessedEventRepository processedEventRepository;
    private final TransactionExecutor transactionExecutor;

    @KafkaListener(topics = "payments", groupId = "${spring.kafka.consumer.group-id}")
    public void handlePaymentEvent(EventEnvelope envelope) {
        handleEvent(envelope);
    }

    @KafkaListener(topics = "orders", groupId = "${spring.kafka.consumer.group-id}")
    public void handleOrderEvent(EventEnvelope envelope) {
        handleEvent(envelope);
    }

    private void handleEvent(EventEnvelope envelope) {
        log.info("Received event: type={}, eventId={}", envelope.getEventType(), envelope.getEventId());

        try {
            if (envelope.hasOrderPaymentCompleted()) {
                processEventIdempotently(envelope, () -> handleOrderPaymentCompleted(envelope));
            } else if (envelope.hasOrderPaymentFailed()) {
                processEventIdempotently(envelope, () -> handleOrderPaymentFailed(envelope));
            } else if (envelope.hasOrderCompleted()) {
                processEventIdempotently(envelope, () -> handleOrderCompleted(envelope));
            } else if (envelope.hasOrderCancelled()) {
                processEventIdempotently(envelope, () -> handleOrderCancelled(envelope));
            } else if (envelope.hasOrderPickingStarted()) {
                processEventIdempotently(envelope, () -> handleOrderPickingStarted(envelope));
            } else if (envelope.hasOrderPickingCompleted()) {
                processEventIdempotently(envelope, () -> handleOrderPickingCompleted(envelope));
            } else if (envelope.hasOrderPickingFailed()) {
                processEventIdempotently(envelope, () -> handleOrderPickingFailed(envelope));
            } else if (envelope.hasOrderDeliveryStarted()) {
                processEventIdempotently(envelope, () -> handleOrderDeliveryStarted(envelope));
            } else if (envelope.hasOrderDelivered()) {
                processEventIdempotently(envelope, () -> handleOrderDelivered(envelope));
            } else if (envelope.hasOrderDeliveryFailed()) {
                processEventIdempotently(envelope, () -> handleOrderDeliveryFailed(envelope));
            } else if (envelope.hasOrderPaymentRefunded()) {
                processEventIdempotently(envelope, () -> handleOrderPaymentRefunded(envelope));
            } else {
                log.warn("Unexpected payload type in topic: {}", envelope.getEventType());
            }
        } catch (Exception e) {
            log.error("Failed to process event: eventId={}, type={}",
                    envelope.getEventId(), envelope.getEventType(), e);
            throw e;
        }
    }

    private void handleOrderPaymentCompleted(EventEnvelope envelope) {
        var event = envelope.getOrderPaymentCompleted();
        log.info("Processing OrderPaymentCompleted: orderId={}, userId={}",
                event.getOrderId(), event.getUserId());

        notificationService.createOrderPaidNotification(
                UUID.fromString(event.getUserId()),
                UUID.fromString(event.getOrderId()),
                event.getAmount().getAmount() + " " + event.getAmount().getCurrency()
        );
    }

    private void handleOrderPaymentFailed(EventEnvelope envelope) {
        var event = envelope.getOrderPaymentFailed();
        log.info("Processing OrderPaymentFailed: orderId={}, userId={}, reason={}",
                event.getOrderId(), event.getUserId(), event.getReason());

        notificationService.createOrderFailedNotification(
                UUID.fromString(event.getUserId()),
                UUID.fromString(event.getOrderId()),
                event.getAmount().getAmount() + " " + event.getAmount().getCurrency(),
                event.getReason()
        );
    }

    private void handleOrderCompleted(EventEnvelope envelope) {
        var event = envelope.getOrderCompleted();
        log.info("Processing OrderCompleted: orderId={}, userId={}",
                event.getOrderId(), event.getUserId());

        notificationService.createOrderConfirmedNotification(
                UUID.fromString(event.getUserId()),
                UUID.fromString(event.getOrderId()),
                event.getAmount().getAmount() + " " + event.getAmount().getCurrency()
        );
    }

    private void handleOrderCancelled(EventEnvelope envelope) {
        var event = envelope.getOrderCancelled();
        log.info("Processing OrderCancelled: orderId={}, userId={}, reason={}",
                event.getOrderId(), event.getUserId(), event.getReason());

        notificationService.createOrderCancelledNotification(
                UUID.fromString(event.getUserId()),
                UUID.fromString(event.getOrderId()),
                event.getAmount().getAmount() + " " + event.getAmount().getCurrency(),
                event.getReason()
        );
    }

    private void handleOrderPickingStarted(EventEnvelope envelope) {
        var event = envelope.getOrderPickingStarted();
        log.info("Processing OrderPickingStarted: orderId={}, userId={}, sku={}",
                event.getOrderId(), event.getUserId(), event.getItemSku());

        notificationService.createOrderPickingStartedNotification(
                UUID.fromString(event.getUserId()),
                UUID.fromString(event.getOrderId())
        );
    }

    private void handleOrderPickingCompleted(EventEnvelope envelope) {
        var event = envelope.getOrderPickingCompleted();
        log.info("Processing OrderPickingCompleted: orderId={}, userId={}, tracking={}",
                event.getOrderId(), event.getUserId(), event.getTrackingNumber());

        notificationService.createOrderPickingCompletedNotification(
                UUID.fromString(event.getUserId()),
                UUID.fromString(event.getOrderId()),
                event.getTrackingNumber()
        );
    }

    private void handleOrderPickingFailed(EventEnvelope envelope) {
        var event = envelope.getOrderPickingFailed();
        log.info("Processing OrderPickingFailed: orderId={}, userId={}, reason={}",
                event.getOrderId(), event.getUserId(), event.getReason());

        notificationService.createOrderPickingFailedNotification(
                UUID.fromString(event.getUserId()),
                UUID.fromString(event.getOrderId()),
                event.getReason()
        );
    }

    private void handleOrderDeliveryStarted(EventEnvelope envelope) {
        var event = envelope.getOrderDeliveryStarted();
        log.info("Processing OrderDeliveryStarted: orderId={}, userId={}, tracking={}",
                event.getOrderId(), event.getUserId(), event.getTrackingNumber());

        notificationService.createOrderDeliveryStartedNotification(
                UUID.fromString(event.getUserId()),
                UUID.fromString(event.getOrderId()),
                event.getTrackingNumber()
        );
    }

    private void handleOrderDelivered(EventEnvelope envelope) {
        var event = envelope.getOrderDelivered();
        log.info("Processing OrderDelivered: orderId={}, userId={}, tracking={}",
                event.getOrderId(), event.getUserId(), event.getTrackingNumber());

        notificationService.createOrderDeliveredNotification(
                UUID.fromString(event.getUserId()),
                UUID.fromString(event.getOrderId()),
                event.getTrackingNumber()
        );
    }

    private void handleOrderDeliveryFailed(EventEnvelope envelope) {
        var event = envelope.getOrderDeliveryFailed();
        log.info("Processing OrderDeliveryFailed: orderId={}, userId={}, reason={}",
                event.getOrderId(), event.getUserId(), event.getReason());

        notificationService.createOrderDeliveryFailedNotification(
                UUID.fromString(event.getUserId()),
                UUID.fromString(event.getOrderId()),
                event.getReason()
        );
    }

    private void handleOrderPaymentRefunded(EventEnvelope envelope) {
        var event = envelope.getOrderPaymentRefunded();
        log.info("Processing OrderPaymentRefunded: orderId={}, userId={}, amount={}",
                event.getOrderId(), event.getUserId(), event.getAmount().getAmount());

        notificationService.createOrderPaymentRefundedNotification(
                UUID.fromString(event.getUserId()),
                UUID.fromString(event.getOrderId()),
                event.getAmount().getAmount() + " " + event.getAmount().getCurrency()
        );
    }

    /**
     * Универсальный метод идемпотентной обработки.
     * Проверяет, не обрабатывали ли мы уже это событие, и если нет — выполняет бизнес-логику
     * и сохраняет eventId в одной транзакции.
     */
    private void processEventIdempotently(EventEnvelope envelope, Runnable action) {
        UUID eventId = UUID.fromString(envelope.getEventId());

        transactionExecutor.execInTransaction(() -> {
            if (processedEventRepository.existsByEventId(eventId)) {
                log.info("Event already processed, skipping: eventId={}, type={}",
                        eventId, envelope.getEventType());
                return;
            }

            log.info("Processing event: eventId={}, type={}", eventId, envelope.getEventType());
            action.run();
            processedEventRepository.save(ProcessedEvent.builder()
                    .eventId(eventId)
                    .eventType(envelope.getEventType())
                    .build());
            log.info("Event processed successfully: eventId={}", eventId);
        });
    }
}