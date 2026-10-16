package ru.spavlochev.delivery.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;
import ru.spavlochev.delivery.openapi.api.CourierApi;
import ru.spavlochev.delivery.openapi.dto.CourierDto;
import ru.spavlochev.delivery.openapi.dto.CreateCourierRequestDto;
import ru.spavlochev.delivery.service.CourierService;

@RestController
@RequiredArgsConstructor
public class CourierController implements CourierApi {

    private final CourierService courierService;

    @Override
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CourierDto> createCourierProfile(CreateCourierRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(courierService.create(dto.getName(), dto.getZone(), dto.getMaxOrdersSlot()));
    }
}
