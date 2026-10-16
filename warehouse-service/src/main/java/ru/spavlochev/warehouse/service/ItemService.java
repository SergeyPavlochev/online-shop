package ru.spavlochev.warehouse.service;

import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.spavlochev.warehouse.entity.Stock;
import ru.spavlochev.warehouse.openapi.dto.ItemDto;
import ru.spavlochev.warehouse.repository.StockRepository;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ItemService {

    private final StockRepository stockRepository;

    @Transactional
    public ItemDto create(String sku, Integer quantity) {
        if (stockRepository.existsBySku(sku)) {
            throw new EntityExistsException("Already exists");
        }

        var stock = stockRepository.save(Stock.builder()
                .sku(sku)
                .quantity(quantity)
                .build());

        log.info("Created Stock: sku {}, quantity {}", sku, quantity);

        return new ItemDto()
                .id(stock.getId())
                .sku(stock.getSku())
                .quantity(stock.getQuantity())
                .reservedQuantity(stock.getReservedQuantity());
    }

    @Transactional
    public ItemDto get(UUID id) {
        var stock = stockRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Item not found by sku"));

        return new ItemDto()
                .id(stock.getId())
                .sku(stock.getSku())
                .quantity(stock.getQuantity())
                .reservedQuantity(stock.getReservedQuantity());
    }
}
