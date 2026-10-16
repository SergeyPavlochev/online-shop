package ru.spavlochev.billing.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import ru.spavlochev.billing.entity.ProcessedEvent;
import ru.spavlochev.billing.repository.ProcessedEventRepository;
import ru.spavlochev.snackbar.events.v1.EventEnvelope;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class EventHandler {

    private final AccountService accountService;
    private final ProcessedEventRepository processedEventRepository;
    private final TransactionExecutor transactionExecutor;

    @KafkaListener(topics = "users", groupId = "${spring.kafka.consumer.group-id}")
    public void handleUserCreated(EventEnvelope envelope) {
        processEventIdempotently(
                envelope,
                () -> {
                    if (!envelope.hasUserRegistered()) {
                        log.warn("Unexpected payload type in users topic: {}", envelope.getEventType());
                        return;
                    }
                    var event = envelope.getUserRegistered();
                    try {
                        accountService.createAccount(UUID.fromString(event.getUserId()));
                    } catch (Exception e) {
                        log.error("Failed to process UserCreated event: eventId={}", envelope.getEventId(), e);
                        throw e;
                    }
                });
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
