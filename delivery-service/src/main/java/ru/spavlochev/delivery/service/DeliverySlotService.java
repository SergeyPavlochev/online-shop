package ru.spavlochev.delivery.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.spavlochev.delivery.entity.DeliverySlot;
import ru.spavlochev.delivery.openapi.dto.DeliverySlotDto;
import ru.spavlochev.delivery.repository.DeliverySlotRepository;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class DeliverySlotService {

    private final DeliverySlotRepository repository;

    public DeliverySlotDto create(String startTime, String endTime, String zone, Integer maxCapacity) {
        var deliverySlot = repository.save(DeliverySlot.builder()
                .startTime(LocalDateTime.parse(startTime))
                .endTime(LocalDateTime.parse(endTime))
                .zone(zone)
                .maxCapacity(maxCapacity)
                .build());
        return new DeliverySlotDto(deliverySlot.getId(), deliverySlot.getStartTime().toString(),
                deliverySlot.getEndTime().toString(), deliverySlot.getZone(), deliverySlot.getMaxCapacity());
    }
}
