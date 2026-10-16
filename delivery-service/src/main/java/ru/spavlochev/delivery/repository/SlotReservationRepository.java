package ru.spavlochev.delivery.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.spavlochev.delivery.entity.SlotReservation;

import java.util.Optional;
import java.util.UUID;

public interface SlotReservationRepository extends JpaRepository<SlotReservation, UUID> {

    Optional<SlotReservation> findByOrderId(UUID orderId);
}
