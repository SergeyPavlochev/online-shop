package ru.spavlochev.warehouse.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;
import ru.spavlochev.warehouse.openapi.api.ItemsStockApi;
import ru.spavlochev.warehouse.openapi.dto.StockCancelRequestDto;
import ru.spavlochev.warehouse.openapi.dto.StockCancelResponseDto;
import ru.spavlochev.warehouse.openapi.dto.StockReserveRequestDto;
import ru.spavlochev.warehouse.openapi.dto.StockReserveResponseDto;
import ru.spavlochev.warehouse.service.StockService;

@RestController
@RequiredArgsConstructor
public class ItemsStockController implements ItemsStockApi {

    private final StockService stockService;

    @Override
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<StockReserveResponseDto> reserve(StockReserveRequestDto dto) {
        stockService.reserve(dto.getOrderId(), dto.getItemSku(), dto.getQuantity());
        return ResponseEntity.ok(new StockReserveResponseDto("reserved"));
    }

    @Override
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<StockCancelResponseDto> cancel(StockCancelRequestDto dto) {
        stockService.cancel(dto.getOrderId());
        return ResponseEntity.ok(new StockCancelResponseDto("cancelled"));
    }
}
