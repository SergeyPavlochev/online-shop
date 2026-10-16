package ru.spavlochev.billing.service;

import com.google.protobuf.Timestamp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.spavlochev.billing.entity.OutboxEvent;
import ru.spavlochev.billing.repository.OutboxEventRepository;
import ru.spavlochev.snackbar.events.v1.EventEnvelope;
import ru.spavlochev.snackbar.events.v1.Money;
import ru.spavlochev.snackbar.events.v1.OrderPaymentCompletedEvent;
import ru.spavlochev.snackbar.events.v1.OrderPaymentFailedEvent;
import ru.spavlochev.snackbar.events.v1.UserAccountCreatedEvent;
import ru.spavlochev.snackbar.events.v1.UserAccountDepositedEvent;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {

    public static final String EVENT_TYPE_ORDER_PAYMENT_COMPLETED = "OrderPaymentCompleted";
    public static final String EVENT_TYPE_ORDER_PAYMENT_FAILED = "OrderPaymentFailed";
    public static final String EVENT_TYPE_USER_ACCOUNT_CREATED = "UserAccountCreated";
    public static final String EVENT_TYPE_USER_ACCOUNT_DEPOSITED = "UserAccountDeposited";

    @Value("${app.kafka.topic.outbox}")
    private String outboxTopic;
    @Value("${app.kafka.topic.batch-size}")
    private int batchSize;
    @Value("${app.outbox.retry-delay-seconds:30}")
    private int retryDelaySeconds;

    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, EventEnvelope> kafkaTemplate;

    @Scheduled(cron = "${app.outbox.schedule-cron:*/10 * * * * *}")
    @Transactional
    public void publishPendingEvents() {
        // Считываем строки в транзакции и удерживаем блокировку до окончания обработки всех событий в пачке
        List<OutboxEvent> events = outboxRepository
                .findByStatusOrderByCreatedAtAsc(OutboxEvent.OutboxStatus.PENDING, PageRequest.of(0, batchSize));

        for (OutboxEvent event : events) {
            try {
                publishEvent(event);
                markAsPublished(event);
            } catch (Exception e) {
                log.error("Failed to publish outbox event: eventId={}, type={}",
                        event.getEventId(), event.getEventType(), e);
                markAsFailed(event);
            }
        }
    }

    @Scheduled(cron = "${app.outbox.schedule-cron:*/10 * * * * *}")
    @Transactional
    public void retryFailedEvents() {
        log.debug("Checking for failed events to retry");

        LocalDateTime now = LocalDateTime.now();
        List<OutboxEvent> failedEvents = outboxRepository
                .findByStatusAndCreatedAtLessThan(OutboxEvent.OutboxStatus.FAILED, now.minusSeconds(retryDelaySeconds));

        if (failedEvents.isEmpty()) {
            log.debug("No failed events to retry");
            return;
        }

        log.info("Found {} failed events to retry", failedEvents.size());

        for (OutboxEvent event : failedEvents) {
            event.setStatus(OutboxEvent.OutboxStatus.PENDING);
            event.setUpdatedAt(now);
            event.setErrorMessage(null);
            outboxRepository.save(event);
            log.info("Retrying failed event: eventId={}", event.getEventId());
        }
    }

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void cleanupOldEvents() {
        int deleted = outboxRepository.cleanupOldPublishedEvents(LocalDateTime.now().minusDays(1));
        if (deleted > 0) {
            log.info("Cleaned up {} old outbox events", deleted);
        }
    }

    private void publishEvent(OutboxEvent event) {
        EventEnvelope envelope = buildEnvelope(event);
        String key = (String) event.getPayload().get("userId");
        SendResult<String, EventEnvelope> result = kafkaTemplate.send(outboxTopic, key, envelope).join();
        log.info("Event published: eventId={}, type={}, topic={}, partition={}, offset={}",
                envelope.getEventId(),
                envelope.getEventType(),
                result.getRecordMetadata().topic(),
                result.getRecordMetadata().partition(),
                result.getRecordMetadata().offset());
    }

    private EventEnvelope buildEnvelope(OutboxEvent event) {
        EventEnvelope.Builder builder = EventEnvelope.newBuilder()
                .setEventId(event.getEventId().toString())
                .setEventType(event.getEventType())
                .setTimestamp(Timestamp.newBuilder()
                        .setSeconds(Instant.now().getEpochSecond())
                        .setNanos(Instant.now().getNano())
                        .build())
                .setCorrelationId(event.getAggregateId().toString())
                .setSource("billing-service");

        Map<String, Object> p = event.getPayload();
        if (EVENT_TYPE_ORDER_PAYMENT_COMPLETED.equals(event.getEventType())) {
            builder.setOrderPaymentCompleted(OrderPaymentCompletedEvent.newBuilder()
                    .setOrderId((String) p.get("orderId"))
                    .setUserId((String) p.get("userId"))
                    .setAmount(Money.newBuilder()
                            .setAmount((String) p.get("amount"))
                            .setCurrency((String) p.get("currency"))
                            .build())
                    .build());
        } else if (EVENT_TYPE_ORDER_PAYMENT_FAILED.equals(event.getEventType())) {
            builder.setOrderPaymentFailed(OrderPaymentFailedEvent.newBuilder()
                    .setOrderId((String) p.get("orderId"))
                    .setUserId((String) p.get("userId"))
                    .setAmount(Money.newBuilder()
                            .setAmount((String) p.get("amount"))
                            .setCurrency((String) p.get("currency"))
                            .build())
                    .setReason((String) p.get("reason"))
                    .build());
        } else if (EVENT_TYPE_USER_ACCOUNT_CREATED.equals(event.getEventType())) {
            builder.setUserAccountCreated(UserAccountCreatedEvent.newBuilder()
                    .setUserId((String) p.get("userId"))
                    .setAccountId((String) p.get("accountId"))
                    .build());
        } else if (EVENT_TYPE_USER_ACCOUNT_DEPOSITED.equals(event.getEventType())) {
            builder.setUserAccountDeposited(UserAccountDepositedEvent.newBuilder()
                    .setUserId((String) p.get("userId"))
                    .setAccountId((String) p.get("accountId"))
                    .setAmount(Money.newBuilder()
                            .setAmount((String) p.get("amount"))
                            .setCurrency((String) p.get("currency"))
                            .build())
                    .build());
        }

        return builder.build();
    }

    private void markAsPublished(OutboxEvent event) {
        event.setStatus(OutboxEvent.OutboxStatus.PUBLISHED);
        event.setPublishedAt(LocalDateTime.now());
        outboxRepository.save(event);
    }

    private void markAsFailed(OutboxEvent event) {
        event.setStatus(OutboxEvent.OutboxStatus.FAILED);
        outboxRepository.save(event);
    }
}

