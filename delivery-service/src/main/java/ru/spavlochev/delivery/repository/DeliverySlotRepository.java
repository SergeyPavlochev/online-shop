package ru.spavlochev.delivery.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.spavlochev.delivery.entity.DeliverySlot;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeliverySlotRepository extends JpaRepository<DeliverySlot, UUID> {

    @Query("SELECT s FROM DeliverySlot s WHERE s.zone = :zone " +
            "AND s.startTime >= :from AND s.startTime < :to " +
            "AND s.currentLoad < s.maxCapacity " +
            "ORDER BY s.startTime ASC")
    List<DeliverySlot> findAvailableSlots(@Param("zone") String zone,
                                          @Param("from") LocalDateTime from,
                                          @Param("to") LocalDateTime to);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM DeliverySlot s WHERE s.id = :id")
    Optional<DeliverySlot> findByIdForUpdate(@Param("id") UUID id);
}
