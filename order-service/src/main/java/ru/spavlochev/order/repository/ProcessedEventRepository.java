package ru.spavlochev.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.spavlochev.order.entity.ProcessedEvent;

import java.util.UUID;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {

    boolean existsByEventId(UUID eventId);
}
