package ru.spavlochev.notification.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.spavlochev.notification.entity.ProcessedEvent;

import java.util.UUID;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {

    boolean existsByEventId(UUID eventId);
}
