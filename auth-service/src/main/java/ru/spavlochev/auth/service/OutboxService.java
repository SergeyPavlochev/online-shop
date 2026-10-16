package ru.spavlochev.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.spavlochev.auth.entity.OutboxEvent;
import ru.spavlochev.auth.repository.OutboxEventRepository;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventRepository outboxRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public void saveEvent(UUID eventId, String eventType, UUID aggregateId, String aggregateType,
                          Map<String, Object> payload) {
        outboxRepository.save(OutboxEvent.builder()
                .eventId(eventId)
                .eventType(eventType)
                .aggregateId(aggregateId)
                .aggregateType(aggregateType)
                .payload(payload)
                .status(OutboxEvent.OutboxStatus.PENDING)
                .build());
    }
}
