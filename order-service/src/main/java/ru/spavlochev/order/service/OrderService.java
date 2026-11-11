package ru.spavlochev.order.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.spavlochev.order.entity.IdempotencyKey;
import ru.spavlochev.order.entity.Order;
import ru.spavlochev.order.openapi.dto.CreateOrderRequestDto;
import ru.spavlochev.order.openapi.dto.OrderDto;
import ru.spavlochev.order.repository.IdempotencyKeyRepository;
import ru.spavlochev.order.repository.OrderRepository;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final OrderSagaOrchestrator orderSagaOrchestrator;

    @Transactional
    public CreateOrderResult createOrderWithIdempotencyKey(UUID userId, CreateOrderRequestDto rqDto,
                                                           String jwtToken, String idempotencyKey) {
        // Проверяем, не обработан ли уже этот ключ
        return idempotencyKeyRepository.findByKey(idempotencyKey)
                .map(existingKey -> {
                    // Ключ уже существует — возвращаем существующий заказ со статусом 200
                    Order order = orderRepository.findById(existingKey.getOrderId())
                            .orElseThrow(() -> new EntityNotFoundException(
                                    "Order not found for idempotency key: " + idempotencyKey));
                    log.info("Idempotency key already completed, returning existing order: key={}, orderId={}",
                            idempotencyKey, order.getId());
                    OrderDto orderDto = new OrderDto()
                            .id(order.getId())
                            .userId(order.getUserId())
                            .amount(order.getAmount().toString())
                            .currency(order.getCurrency())
                            .status(OrderDto.StatusEnum.fromValue(order.getStatus().name()))
                            .createdAt(order.getCreatedAt().atOffset(ZoneOffset.UTC));
                    return new CreateOrderResult(HttpStatus.OK, orderDto);
                })
                .orElseGet(() -> {
                    // Новый ключ — создаём заказ
                    Order order = orderSagaOrchestrator.startOrderSaga(
                            userId,
                            new BigDecimal(rqDto.getAmount()),
                            rqDto.getCurrency(),
                            rqDto.getItemSku().toString(),
                            rqDto.getQuantity(),
                            rqDto.getDeliverySlot().toString(),
                            jwtToken);

                    // Сохраняем idempotency key
                    idempotencyKeyRepository.save(IdempotencyKey.builder()
                            .key(idempotencyKey)
                            .orderId(order.getId())
                            .status(IdempotencyKey.Status.COMPLETED)
                            .build());

                    log.info("Order created with idempotency key: key={}, orderId={}",
                            idempotencyKey, order.getId());

                    OrderDto orderDto = new OrderDto()
                            .id(order.getId())
                            .userId(order.getUserId())
                            .amount(order.getAmount().toString())
                            .currency(order.getCurrency())
                            .status(OrderDto.StatusEnum.fromValue(order.getStatus().name()))
                            .createdAt(order.getCreatedAt().atOffset(ZoneOffset.UTC));
                    return new CreateOrderResult(HttpStatus.ACCEPTED, orderDto);
                });
    }

    @Transactional(readOnly = true)
    public OrderDto getOrder(UUID userId, UUID orderId) {
        return orderRepository.findById(orderId)
                .filter(order -> userId.equals(order.getUserId()))
                .map(order -> new OrderDto()
                        .id(order.getId())
                        .userId(order.getUserId())
                        .amount(order.getAmount().toString())
                        .currency(order.getCurrency())
                        .status(OrderDto.StatusEnum.fromValue(order.getStatus().name()))
                        .createdAt(order.getCreatedAt().atOffset(ZoneOffset.UTC)))
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + orderId));
    }

    @Transactional
    public void cancelOrder(UUID userId, UUID orderId, String reason, String jwtToken) {
        Order order = orderRepository.findById(orderId)
                .filter(o -> userId.equals(o.getUserId()))
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + orderId));

        orderSagaOrchestrator.cancelOrder(orderId, reason, jwtToken);

        log.info("Order cancelled: orderId={}, reason={}", orderId, reason);
    }
}
