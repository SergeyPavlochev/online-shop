package ru.spavlochev.delivery.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;
import ru.spavlochev.delivery.openapi.api.DeliveryApi;
import ru.spavlochev.delivery.openapi.dto.AvailableSlotDto;
import ru.spavlochev.delivery.openapi.dto.DeliveryCancelRequestDto;
import ru.spavlochev.delivery.openapi.dto.DeliveryCancelResponseDto;
import ru.spavlochev.delivery.openapi.dto.DeliveryReserveRequestDto;
import ru.spavlochev.delivery.openapi.dto.DeliveryReserveResponseDto;
import ru.spavlochev.delivery.service.DeliveryService;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class DeliveryController implements DeliveryApi {

    private final DeliveryService deliveryService;

    @Override
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<AvailableSlotDto>> getAvailableSlots(String zone, String date) {
        return ResponseEntity.ok(deliveryService.getAvailableSlots(zone, LocalDateTime.parse(date)));
    }

    @Override
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<DeliveryReserveResponseDto> reserve(DeliveryReserveRequestDto dto) {
        deliveryService.reserve(dto.getOrderId(), dto.getSlotId());
        return ResponseEntity.ok(new DeliveryReserveResponseDto("reserved"));
    }

    @Override
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<DeliveryCancelResponseDto> cancel(DeliveryCancelRequestDto dto) {
        deliveryService.cancel(dto.getOrderId());
        return ResponseEntity.ok(new DeliveryCancelResponseDto("cancelled"));
    }
}
