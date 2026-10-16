package ru.spavlochev.warehouse.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;
import ru.spavlochev.warehouse.openapi.api.ItemsApi;
import ru.spavlochev.warehouse.openapi.dto.CreateItemRequestDto;
import ru.spavlochev.warehouse.openapi.dto.ItemDto;
import ru.spavlochev.warehouse.service.ItemService;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ItemsController implements ItemsApi {

    private final ItemService itemService;

    @Override
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ItemDto> createItem(CreateItemRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(itemService.create(dto.getItemSku(), dto.getQuantity()));
    }

    @Override
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ItemDto> getItem(UUID id) {
        return ResponseEntity.ok(itemService.get(id));
    }
}
