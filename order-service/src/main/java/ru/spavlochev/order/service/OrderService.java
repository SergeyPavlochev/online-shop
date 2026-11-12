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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final OutboxService outboxService;
    private final OrderSagaOrchestrator orderSagaOrchestrator;

    @Transactional
    public CreateOrderResult createOrder(UUID userId, CreateOrderRequestDto rqDto, UUID idempotencyKey) {
        // Атомарная вставка ключа через native query ON CONFLICT DO NOTHING
        int rowsInserted = idempotencyKeyRepository.insertWithConflictCheck(idempotencyKey, userId);
        if (rowsInserted == 0) {
            // Ключ уже существует — обрабатываем конфликт
            return handleExistingKey(idempotencyKey, userId);
        }

        // rowsInserted == 1 — это первый запрос, создаём заказ
        return createNewOrder(userId, rqDto, idempotencyKey);
    }

    private CreateOrderResult handleExistingKey(UUID idempotencyKey, UUID userId) {
        var idempotencyKeyEntity = idempotencyKeyRepository.findByKey(idempotencyKey)
                .orElseThrow(() -> new IllegalStateException(
                        "Idempotency key not found after conflict: " + idempotencyKey));

        // Защита от cross-user: ключ принадлежит другому пользователю
        if (!idempotencyKeyEntity.getUserId().equals(userId)) {
            throw new IllegalStateException(
                    "Idempotency key already used by another user: " + idempotencyKey);
        }

        switch (idempotencyKeyEntity.getStatus()) {
            case IN_PROGRESS:
                // Предыдущий запрос ещё обрабатывается
                Order pendingOrder = orderRepository.findById(idempotencyKeyEntity.getOrderId())
                        .orElseThrow(() -> new EntityNotFoundException(
                                "Order not found for idempotency key: " + idempotencyKey));
                log.info("Idempotency key IN_PROGRESS, returning pending order: key={}, orderId={}",
                        idempotencyKey, pendingOrder.getId());
                return new CreateOrderResult(HttpStatus.ACCEPTED, buildOrderDto(pendingOrder));

            case SUCCESS:
                // Предыдущий запрос завершён — возвращаем кэшированный ответ
                if (idempotencyKeyEntity.getResponseBody() != null) {
                    log.info("Idempotency key SUCCESS, returning cached response: key={}", idempotencyKey);
                    return new CreateOrderResult(
                            HttpStatus.OK, mapToDto(idempotencyKeyEntity.getResponseBody()));
                }
                // Fallback: читаем заказ из БД
                Order completedOrder = orderRepository.findById(idempotencyKeyEntity.getOrderId())
                        .orElseThrow(() -> new EntityNotFoundException(
                                "Order not found for idempotency key: " + idempotencyKey));
                log.info("Idempotency key SUCCESS (no cached body), returning order from DB: key={}, orderId={}",
                        idempotencyKey, completedOrder.getId());
                return new CreateOrderResult(HttpStatus.OK, buildOrderDto(completedOrder));

            case FAILED:
                log.warn("Idempotency key FAILED: {}", idempotencyKey);
                throw new IllegalStateException(
                        "Idempotency key already used and operation failed: " + idempotencyKey);

            default:
                throw new IllegalStateException(
                        "Unknown idempotency key status: " + idempotencyKeyEntity.getStatus());
        }
    }

    private CreateOrderResult createNewOrder(UUID userId, CreateOrderRequestDto rqDto, UUID idempotencyKey) {
        // Создаём заказ (только в БД, без саги)
        Order order = orderRepository.save(Order.builder()
                .userId(userId)
                .amount(new BigDecimal(rqDto.getAmount()))
                .currency(rqDto.getCurrency())
                .status(Order.OrderStatus.PENDING)
                .build());

        // Сохраняем событие в Outbox (та же транзакция)
        outboxService.saveEvent("OrderCreated", order.getId(), "Order",
                Map.of(
                        "orderId", order.getId().toString(),
                        "userId", userId.toString(),
                        "amount", rqDto.getAmount(),
                        "currency", rqDto.getCurrency(),
                        "itemSku", rqDto.getItemSku().toString(),
                        "quantity", String.valueOf(rqDto.getQuantity()),
                        "deliverySlot", rqDto.getDeliverySlot().toString()
                ));

        // Обновляем ключ на SUCCESS
        var idempotencyKeyEntity = idempotencyKeyRepository.findByKey(idempotencyKey)
                .orElseThrow();
        idempotencyKeyEntity.setOrderId(order.getId());
        idempotencyKeyEntity.setStatus(IdempotencyKey.Status.SUCCESS);
        idempotencyKeyEntity.setOrderId(order.getId());
        OrderDto cachedDto = buildOrderDto(order);
        idempotencyKeyEntity.setResponseBody(dtoToMap(cachedDto));
        idempotencyKeyEntity.setResponseStatusCode(HttpStatus.ACCEPTED.value());
        idempotencyKeyRepository.save(idempotencyKeyEntity);

        log.info("Order created with idempotency idempotencyKeyEntity: idempotencyKeyEntity={}, orderId={}",
                idempotencyKey, order.getId());

        return new CreateOrderResult(HttpStatus.ACCEPTED, cachedDto);
    }

    private OrderDto buildOrderDto(Order order) {
        return new OrderDto()
                .id(order.getId())
                .userId(order.getUserId())
                .amount(order.getAmount().toString())
                .currency(order.getCurrency())
                .status(OrderDto.StatusEnum.fromValue(order.getStatus().name()))
                .createdAt(order.getCreatedAt().atOffset(ZoneOffset.UTC));
    }

    private OrderDto mapToDto(Map<String, Object> map) {
        OrderDto dto = new OrderDto();
        if (map.containsKey("id")) {
            dto.setId(UUID.fromString((String) map.get("id")));
        }
        if (map.containsKey("userId")) {
            dto.setUserId(UUID.fromString((String) map.get("userId")));
        }
        if (map.containsKey("amount")) {
            dto.setAmount((String) map.get("amount"));
        }
        if (map.containsKey("currency")) {
            dto.setCurrency((String) map.get("currency"));
        }
        if (map.containsKey("status")) {
            dto.setStatus(OrderDto.StatusEnum.fromValue((String) map.get("status")));
        }
        if (map.containsKey("createdAt")) {
            dto.setCreatedAt(OffsetDateTime.parse((String) map.get("createdAt")));
        }
        return dto;
    }

    private Map<String, Object> dtoToMap(OrderDto dto) {
        return Map.of(
                "id", dto.getId().toString(),
                "userId", dto.getUserId().toString(),
                "amount", dto.getAmount(),
                "currency", dto.getCurrency(),
                "status", dto.getStatus().getValue(),
                "createdAt", dto.getCreatedAt().toString()
        );
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
