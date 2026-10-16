package ru.spavlochev.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import ru.spavlochev.notification.entity.ProcessedEvent;
import ru.spavlochev.notification.repository.ProcessedEventRepository;
import ru.spavlochev.snackbar.events.v1.EventEnvelope;

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
            } else {
                log.warn("Unexpected payload type in payments topic: {}", envelope.getEventType());
            }
        } catch (Exception e) {
            log.error("Failed to process payment event: eventId={}, type={}",
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

    /**
     * Универсальный метод идемпотентной обработки.
     * Проверяет, не обрабатывали ли мы уже это событие, и если нет — выполняет бизнес-логику
     * и сохраняет eventId в одной транзакции.
     */
    private void processEventIdempotently(EventEnvelope envelope, Runnable action) {
        UUID eventId = UUID.fromString(envelope.getEventId());

        // 1. Проверяем, не обработано ли уже событие
        boolean eventWasProcessed = transactionExecutor.execInTransaction(() ->
                processedEventRepository.existsByEventId(eventId));
        if (eventWasProcessed) {
            log.info("Event already processed, skipping: eventId={}, type={}",
                    eventId, envelope.getEventType());
            return;
        }

        // 2. Выполняем бизнес-логику и сохраняем eventId в одной транзакции
        // Если бизнес-логика упадет — транзакция откатится, eventId не сохранится
        // Если сохранение eventId упадет — откатится и бизнес-логика
        transactionExecutor.execInTransaction(() -> {
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