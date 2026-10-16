package ru.spavlochev.warehouse.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.spavlochev.warehouse.entity.Reservation;
import ru.spavlochev.warehouse.entity.Stock;
import ru.spavlochev.warehouse.repository.ReservationRepository;
import ru.spavlochev.warehouse.repository.StockRepository;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockService {

    private final StockRepository stockRepository;
    private final ReservationRepository reservationRepository;

    @Transactional
    public void reserve(UUID orderId, String itemSku, int quantity) {
        if (reservationRepository.existsByOrderId(orderId)) {
            return;
        }

        Stock stock = stockRepository.findBySkuForUpdate(itemSku)
                .orElseThrow(() -> new EntityNotFoundException("Item not found"));

        if (stock.getAvailableQuantity() < quantity) {
            throw new IllegalStateException("Insufficient stock");
        }

        stock.setReservedQuantity(stock.getReservedQuantity() + quantity);
        stockRepository.save(stock);

        reservationRepository.save(Reservation.builder()
                .orderId(orderId)
                .itemSku(itemSku)
                .quantity(quantity)
                .build());
    }

    @Transactional
    public void cancel(UUID orderId) {
        Reservation reservation = reservationRepository.findByOrderId(orderId)
                .orElse(null);
        if (reservation == null) {
            return;
        }

        Stock stock = stockRepository.findBySkuForUpdate(reservation.getItemSku())
                .orElseThrow(() -> new EntityNotFoundException("Item not found"));
        stock.setReservedQuantity(stock.getReservedQuantity() - reservation.getQuantity());
        stockRepository.save(stock);

        reservationRepository.delete(reservation);
    }
}
