package ru.spavlochev.delivery.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.spavlochev.delivery.entity.SlotReservation;
import ru.spavlochev.delivery.repository.SlotReservationRepository;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeliveryOperationService {

    private final SlotReservationRepository reservationRepository;
    private final OutboxService outboxService;

    @Transactional
    public void assign(UUID orderId, String trackingNumber, UUID courierId) {
        SlotReservation reservation = reservationRepository.findByOrderId(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Reservation not found for orderId: " + orderId));

        reservation.setStatus(SlotReservation.ReservationStatus.IN_DELIVERY);
        reservationRepository.save(reservation);

        outboxService.saveEvent("OrderDeliveryStarted", orderId, "Order",
                Map.of("orderId", orderId.toString(),
                        "userId", "",
                        "trackingNumber", trackingNumber,
                        "courierId", courierId.toString()));

        log.info("Delivery started: orderId={}, tracking={}, courier={}", orderId, trackingNumber, courierId);
    }

    @Transactional
    public void deliver(UUID orderId, String trackingNumber) {
        SlotReservation reservation = reservationRepository.findByOrderId(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Reservation not found for orderId: " + orderId));

        reservation.setStatus(SlotReservation.ReservationStatus.COMPLETED);
        reservationRepository.save(reservation);

        outboxService.saveEvent("OrderDelivered", orderId, "Order",
                Map.of("orderId", orderId.toString(),
                        "userId", "",
                        "trackingNumber", trackingNumber));

        log.info("Order delivered: orderId={}, tracking={}", orderId, trackingNumber);
    }

    @Transactional
    public void fail(UUID orderId, String trackingNumber, String reason) {
        SlotReservation reservation = reservationRepository.findByOrderId(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Reservation not found for orderId: " + orderId));

        reservation.setStatus(SlotReservation.ReservationStatus.CANCELLED);
        reservationRepository.save(reservation);

        outboxService.saveEvent("OrderDeliveryFailed", orderId, "Order",
                Map.of("orderId", orderId.toString(),
                        "userId", "",
                        "trackingNumber", trackingNumber,
                        "reason", reason));

        log.warn("Delivery failed: orderId={}, reason={}", orderId, reason);
    }
}
