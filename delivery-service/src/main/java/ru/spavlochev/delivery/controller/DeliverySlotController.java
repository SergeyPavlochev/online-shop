package ru.spavlochev.delivery.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;
import ru.spavlochev.delivery.openapi.api.DeliverySlotApi;
import ru.spavlochev.delivery.openapi.dto.CreateDeliverySlotRequestDto;
import ru.spavlochev.delivery.openapi.dto.DeliverySlotDto;
import ru.spavlochev.delivery.service.DeliverySlotService;

@RestController
@RequiredArgsConstructor
public class DeliverySlotController implements DeliverySlotApi {

    private final DeliverySlotService deliverySlotService;

    @Override
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<DeliverySlotDto> create(CreateDeliverySlotRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(deliverySlotService.create(dto.getStartTime(), dto.getEndTime(), dto.getZone(), dto.getMaxCapacity()));
    }
}
