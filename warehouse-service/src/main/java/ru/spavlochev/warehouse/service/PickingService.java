package ru.spavlochev.warehouse.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.spavlochev.warehouse.repository.ReservationRepository;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PickingService {

    private final ReservationRepository reservationRepository;
    private final OutboxService outboxService;

    @Transactional
    public void start(UUID orderId, String itemSku) {
        reservationRepository.findByOrderId(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Reservation not found for orderId: " + orderId));

        outboxService.saveEvent("OrderPickingStarted", orderId, "Order",
                Map.of("orderId", orderId.toString(),
                        "userId", "",
                        "itemSku", itemSku));

        log.info("Picking started for orderId={}, sku={}", orderId, itemSku);
    }

    @Transactional
    public void complete(UUID orderId, String trackingNumber) {
        reservationRepository.findByOrderId(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Reservation not found for orderId: " + orderId));

        outboxService.saveEvent("OrderPickingCompleted", orderId, "Order",
                Map.of("orderId", orderId.toString(),
                        "userId", "",
                        "trackingNumber", trackingNumber));

        log.info("Picking completed for orderId={}, tracking={}", orderId, trackingNumber);
    }

    @Transactional
    public void fail(UUID orderId, String reason) {
        reservationRepository.findByOrderId(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Reservation not found for orderId: " + orderId));

        outboxService.saveEvent("OrderPickingFailed", orderId, "Order",
                Map.of("orderId", orderId.toString(),
                        "userId", "",
                        "reason", reason));

        log.warn("Picking failed for orderId={}, reason={}", orderId, reason);
    }
}
