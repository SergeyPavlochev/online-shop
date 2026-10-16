package ru.spavlochev.billing.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.spavlochev.billing.entity.ProcessedEvent;

import java.util.UUID;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {

    boolean existsByEventId(UUID eventId);
}