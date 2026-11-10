package ru.spavlochev.delivery.service;

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
import ru.spavlochev.delivery.entity.OutboxEvent;
import ru.spavlochev.delivery.repository.OutboxEventRepository;
import ru.spavlochev.onlineshop.events.v1.EventEnvelope;
import ru.spavlochev.onlineshop.events.v1.OrderDeliveredEvent;
import ru.spavlochev.onlineshop.events.v1.OrderDeliveryFailedEvent;
import ru.spavlochev.onlineshop.events.v1.OrderDeliveryStartedEvent;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {

    public static final String EVENT_TYPE_DELIVERY_STARTED = "OrderDeliveryStarted";
    public static final String EVENT_TYPE_DELIVERED = "OrderDelivered";
    public static final String EVENT_TYPE_DELIVERY_FAILED = "OrderDeliveryFailed";

    public static final String DELIVERY_EVENTS_TOPIC = "delivery-events";

    @Value("${app.kafka.topic.outbox}")
    private String outboxTopic;

    @Value("${app.kafka.topic.batch-size}")
    private int batchSize;

    @Value("${app.outbox.retry-delay-seconds:30}")
    private int retryDelaySeconds;

    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, EventEnvelope> kafkaTemplate;

    @Scheduled(cron = "${app.outbox.schedule-cron:*/1 * * * * *}")
    @Transactional
    public void publishPendingEvents() {
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
        LocalDateTime now = LocalDateTime.now();
        List<OutboxEvent> failedEvents = outboxRepository
                .findByStatusAndCreatedAtLessThan(OutboxEvent.OutboxStatus.FAILED, now.minusSeconds(retryDelaySeconds));

        if (failedEvents.isEmpty()) {
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

    @SuppressWarnings("unchecked")
    private void publishEvent(OutboxEvent event) {
        EventEnvelope envelope = buildEnvelope(event);
        String key = (String) event.getPayload().get("orderId");
        SendResult<String, EventEnvelope> result = kafkaTemplate.send(outboxTopic, key, envelope).join();
        log.info("Event published: eventId={}, type={}, topic={}, partition={}, offset={}",
                envelope.getEventId(), envelope.getEventType(),
                result.getRecordMetadata().topic(),
                result.getRecordMetadata().partition(),
                result.getRecordMetadata().offset());
    }

    @SuppressWarnings("unchecked")
    private EventEnvelope buildEnvelope(OutboxEvent event) {
        EventEnvelope.Builder builder = EventEnvelope.newBuilder()
                .setEventId(event.getEventId().toString())
                .setEventType(event.getEventType())
                .setTimestamp(Timestamp.newBuilder()
                        .setSeconds(Instant.now().getEpochSecond())
                        .setNanos(Instant.now().getNano())
                        .build())
                .setCorrelationId(event.getAggregateId().toString())
                .setSource("delivery-service");

        Map<String, Object> p = event.getPayload();
        if (EVENT_TYPE_DELIVERY_STARTED.equals(event.getEventType())) {
            builder.setOrderDeliveryStarted(OrderDeliveryStartedEvent.newBuilder()
                    .setOrderId((String) p.get("orderId"))
                    .setUserId((String) p.get("userId"))
                    .setTrackingNumber((String) p.get("trackingNumber"))
                    .setCourierId((String) p.get("courierId"))
                    .build());
        } else if (EVENT_TYPE_DELIVERED.equals(event.getEventType())) {
            builder.setOrderDelivered(OrderDeliveredEvent.newBuilder()
                    .setOrderId((String) p.get("orderId"))
                    .setUserId((String) p.get("userId"))
                    .setTrackingNumber((String) p.get("trackingNumber"))
                    .build());
        } else if (EVENT_TYPE_DELIVERY_FAILED.equals(event.getEventType())) {
            builder.setOrderDeliveryFailed(OrderDeliveryFailedEvent.newBuilder()
                    .setOrderId((String) p.get("orderId"))
                    .setUserId((String) p.get("userId"))
                    .setTrackingNumber((String) p.get("trackingNumber"))
                    .setReason((String) p.get("reason"))
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
