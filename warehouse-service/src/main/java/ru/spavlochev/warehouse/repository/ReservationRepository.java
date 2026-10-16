package ru.spavlochev.warehouse.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.spavlochev.warehouse.entity.Reservation;

import java.util.Optional;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    boolean existsByOrderId(UUID orderId);

    Optional<Reservation> findByOrderId(UUID orderId);
}
