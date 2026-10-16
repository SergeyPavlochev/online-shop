package ru.spavlochev.delivery.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.spavlochev.delivery.entity.Courier;
import ru.spavlochev.delivery.openapi.dto.CourierDto;
import ru.spavlochev.delivery.repository.CourierRepository;

@Service
@RequiredArgsConstructor
public class CourierService {

    private final CourierRepository repository;

    public CourierDto create(String name, String zone, Integer maxOrdersSlot) {
        var courier = repository.save(Courier.builder()
                .name(name)
                .zone(zone)
                .maxOrdersSlot(maxOrdersSlot)
                .build());
        return new CourierDto(courier.getId(), courier.getName(), courier.getZone(), courier.getMaxOrdersSlot());
    }
}
