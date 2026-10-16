package ru.spavlochev.delivery.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.spavlochev.delivery.entity.Courier;
import ru.spavlochev.delivery.entity.DeliverySlot;
import ru.spavlochev.delivery.entity.SlotReservation;
import ru.spavlochev.delivery.openapi.dto.AvailableSlotDto;
import ru.spavlochev.delivery.repository.CourierRepository;
import ru.spavlochev.delivery.repository.DeliverySlotRepository;
import ru.spavlochev.delivery.repository.SlotReservationRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeliveryService {

    private final DeliverySlotRepository slotRepository;
    private final SlotReservationRepository reservationRepository;
    private final CourierRepository courierRepository;

    @Transactional(readOnly = true)
    public List<AvailableSlotDto> getAvailableSlots(String zone, LocalDateTime date) {
        LocalDateTime from = date.toLocalDate().atStartOfDay();
        LocalDateTime to = from.plusDays(1);

        return slotRepository.findAvailableSlots(zone, from, to).stream()
                .map(slot -> new AvailableSlotDto(
                        slot.getId(),
                        slot.getStartTime().toString(),
                        slot.getEndTime().toString(),
                        slot.getZone(),
                        slot.getMaxCapacity() - slot.getCurrentLoad()
                ))
                .toList();
    }

    @Transactional
    public void reserve(UUID orderId, UUID slotId) {
        log.info("Reserving delivery: orderId={}, slotId={}", orderId, slotId);

        if (reservationRepository.findByOrderId(orderId).isPresent()) {
            log.info("Reservation already exists for orderId={}", orderId);
            return;
        }

        DeliverySlot slot = slotRepository.findByIdForUpdate(slotId)
                .orElseThrow(() -> new IllegalStateException("Slot not found: " + slotId));

        if (slot.getCurrentLoad() >= slot.getMaxCapacity()) {
            throw new IllegalStateException("Slot is fully booked: " + slotId);
        }

        Courier courier = courierRepository.findFreeCourierForSlot(slotId, slot.getZone())
                .orElseThrow(() -> new IllegalStateException(
                        "No free couriers in zone " + slot.getZone() + " for slot " + slotId));

        SlotReservation reservation = SlotReservation.builder()
                .orderId(orderId)
                .slotId(slotId)
                .courierId(courier.getId())
                .status(SlotReservation.ReservationStatus.RESERVED)
                .build();
        reservationRepository.save(reservation);

        slot.setCurrentLoad(slot.getCurrentLoad() + 1);
        slotRepository.save(slot);

        log.info("Delivery reserved: orderId={}, courierId={}, slot={}-{}",
                orderId, courier.getId(), slot.getStartTime(), slot.getEndTime());
    }

    @Transactional
    public void cancel(UUID orderId) {
        log.info("Cancelling delivery reservation: orderId={}", orderId);

        SlotReservation reservation = reservationRepository.findByOrderId(orderId).orElse(null);
        if (reservation == null || reservation.getStatus() == SlotReservation.ReservationStatus.CANCELLED) {
            log.info("Reservation already cancelled or not found for orderId={}", orderId);
            return;
        }

        // Освобождаем место в слоте
        DeliverySlot slot = slotRepository.findByIdForUpdate(reservation.getSlotId()).orElseThrow();
        slot.setCurrentLoad(Math.max(0, slot.getCurrentLoad() - 1));
        slotRepository.save(slot);

        // Помечаем резервацию как отмененную
        reservation.setStatus(SlotReservation.ReservationStatus.CANCELLED);
        reservationRepository.save(reservation);

        log.info("Delivery cancelled: orderId={}, slotId={}", orderId, reservation.getSlotId());
    }
}
