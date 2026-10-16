package ru.spavlochev.order.repository;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.hibernate.cfg.AvailableSettings;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import ru.spavlochev.order.entity.OutboxEvent;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = AvailableSettings.JAKARTA_LOCK_TIMEOUT, value = "-2")) // -2 = SKIP LOCKED
    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxEvent.OutboxStatus status, Pageable pageable);

    @Modifying
    @Query("DELETE FROM OutboxEvent e WHERE e.status = 'PUBLISHED' AND e.publishedAt < :cutOffDateTime")
    int cleanupOldPublishedEvents(LocalDateTime cutOffDateTime);
}
