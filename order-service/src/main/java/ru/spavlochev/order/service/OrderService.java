package ru.spavlochev.order.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.spavlochev.order.openapi.dto.CreateOrderRequestDto;
import ru.spavlochev.order.openapi.dto.OrderDto;
import ru.spavlochev.order.repository.OrderRepository;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderSagaOrchestrator orderSagaOrchestrator;

    @Transactional
    public OrderDto createOrder(UUID userId, CreateOrderRequestDto rqDto) {
        var order = orderSagaOrchestrator.startOrderSaga(
                userId,
                new BigDecimal(rqDto.getAmount()),
                rqDto.getCurrency(),
                rqDto.getItemSku().toString(),
                rqDto.getQuantity(),
                rqDto.getDeliverySlot().toString());

        log.info("Order created: orderId={}, userId={}, status={}",
                order.getId(), userId, order.getStatus());

        return new OrderDto()
                .id(order.getId())
                .userId(order.getUserId())
                .amount(order.getAmount().toString())
                .currency(order.getCurrency())
                .status(OrderDto.StatusEnum.fromValue(order.getStatus().name()))
                .createdAt(order.getCreatedAt().atOffset(ZoneOffset.UTC));
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
}
