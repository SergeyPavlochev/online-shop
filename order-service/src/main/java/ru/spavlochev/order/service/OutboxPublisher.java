package ru.spavlochev.order.service;

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
import ru.spavlochev.onlineshop.events.v1.EventEnvelope;
import ru.spavlochev.onlineshop.events.v1.Money;
import ru.spavlochev.onlineshop.events.v1.OrderCancelledEvent;
import ru.spavlochev.onlineshop.events.v1.OrderCreatedEvent;
import ru.spavlochev.onlineshop.events.v1.OrderDeliveredEvent;
import ru.spavlochev.onlineshop.events.v1.OrderPaymentCompletedEvent;
import ru.spavlochev.order.entity.OutboxEvent;
import ru.spavlochev.order.repository.OutboxEventRepository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {

    @Value("${app.kafka.topic.outbox}")
    private String outboxTopic;
    @Value("${app.kafka.topic.batch-size:50}")
    private int kafkaBatchSize;
    @Value("${app.outbox.retry-delay-seconds:30}")
    private int retryDelaySeconds;

    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, EventEnvelope> kafkaTemplate;

    @Scheduled(cron = "${app.outbox.schedule-cron:*/1 * * * * *}")
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> events = outboxRepository
                .findByStatusOrderByCreatedAtAsc(OutboxEvent.OutboxStatus.PENDING, PageRequest.of(0, kafkaBatchSize));
        if (events.isEmpty()) {
            return;
        }

        log.info("Processing {} pending outbox events", events.size());

        for (OutboxEvent event : events) {
            try {
                publishEvent(event);
                markAsPublished(event);
            } catch (Exception e) {
                log.error("Failed to publish outbox event: eventId={}, type={}",
                        event.getEventId(), event.getEventType(), e);
                markAsFailed(event, e.getMessage());
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
                .setSource("order-service");

        Map<String, Object> p = event.getPayload();
        if ("OrderPaymentCompleted".equals(event.getEventType())) {
            builder.setOrderPaymentCompleted(OrderPaymentCompletedEvent.newBuilder()
                    .setOrderId((String) p.get("orderId"))
                    .setUserId((String) p.get("userId"))
                    .setAmount(Money.newBuilder()
                            .setAmount((String) p.get("amount"))
                            .setCurrency((String) p.get("currency"))
                            .build())
                    .build());
        } else if ("OrderCancelled".equals(event.getEventType())) {
            builder.setOrderCancelled(OrderCancelledEvent.newBuilder()
                    .setOrderId((String) p.get("orderId"))
                    .setUserId((String) p.get("userId"))
                    .setAmount(Money.newBuilder()
                            .setAmount((String) p.get("amount"))
                            .setCurrency((String) p.get("currency"))
                            .build())
                    .setReason((String) p.get("reason"))
                    .build());
        } else if ("OrderDelivered".equals(event.getEventType())) {
            builder.setOrderDelivered(OrderDeliveredEvent.newBuilder()
                    .setOrderId((String) p.get("orderId"))
                    .setUserId((String) p.get("userId"))
                    .setTrackingNumber((String) p.get("trackingNumber"))
                    .build());
        } else if ("OrderCreated".equals(event.getEventType())) {
            builder.setOrderCreated(OrderCreatedEvent.newBuilder()
                    .setOrderId((String) p.get("orderId"))
                    .setUserId((String) p.get("userId"))
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

    private void markAsFailed(OutboxEvent event, String errorMessage) {
        event.setStatus(OutboxEvent.OutboxStatus.FAILED);
        event.setErrorMessage(errorMessage);
        outboxRepository.save(event);
    }
}
