package ru.spavlochev.order.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import ru.spavlochev.order.dto.DeliveryCancelRequest;
import ru.spavlochev.order.dto.DeliveryReserveRequest;
import ru.spavlochev.order.dto.DeliveryReserveResponse;
import ru.spavlochev.order.dto.PaymentCancelRequest;
import ru.spavlochev.order.dto.PaymentConfirmRequest;
import ru.spavlochev.order.dto.PaymentReserveRequest;
import ru.spavlochev.order.dto.PaymentReserveResponse;
import ru.spavlochev.order.dto.StockCancelRequest;
import ru.spavlochev.order.dto.StockReserveRequest;
import ru.spavlochev.order.dto.StockReserveResponse;
import ru.spavlochev.order.entity.Order;
import ru.spavlochev.order.entity.OrderSagaState;
import ru.spavlochev.order.entity.OrderSagaState.SagaStatus;
import ru.spavlochev.order.repository.OrderRepository;
import ru.spavlochev.order.repository.OrderSagaStateRepository;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("BlockingMethodInNonBlockingContext")
public class OrderSagaOrchestrator {

    private static final Duration STEP_TIMEOUT = Duration.ofSeconds(10);

    private final OrderRepository orderRepository;
    private final OrderSagaStateRepository sagaStateRepository;
    private final OutboxService outboxService;
    private final TransactionExecutor transactionExecutor;

    private final WebClient billingWebClient;
    private final WebClient warehouseWebClient;
    private final WebClient deliveryWebClient;

    @Transactional
    public Order startOrderSaga(UUID userId, BigDecimal amount, String currency,
                                String itemSku, int quantity, String deliverySlot) {
        // 1. Создаем заказ в статусе PENDING
        Order order = orderRepository.save(Order.builder()
                .userId(userId)
                .amount(amount)
                .currency(currency)
                .status(Order.OrderStatus.PENDING)
                .build());

        // 2. Создаем запись о саге
        OrderSagaState state = sagaStateRepository.save(OrderSagaState.builder()
                .orderId(order.getId())
                .status(SagaStatus.STARTED)
                .build());

        // 3. Запускаем сагу
        executeSagaSteps(state, itemSku, quantity, deliverySlot)
                .subscribe();

        return order;
    }

    private Mono<Void> executeSagaSteps(OrderSagaState state,
                                        String itemSku, int quantity, String deliverySlot) {
        return reservePayment(state)
                .then(Mono.fromRunnable(() -> updateStatus(state, SagaStatus.PAYMENT_RESERVED)))
                .then(reserveStock(state, itemSku, quantity))
                .then(Mono.fromRunnable(() -> updateStatus(state, SagaStatus.STOCK_RESERVED)))
                .then(reserveDelivery(state, deliverySlot))
                .then(Mono.fromRunnable(() -> updateStatus(state, SagaStatus.DELIVERY_RESERVED)))
                .then(confirmPayment(state))
                .then(Mono.fromRunnable(() -> finalizeSaga(state)))
                .onErrorResume(ex -> {
                    log.error("Saga failed at step {}: {}", state.getStatus(), ex.getMessage());
                    return compensateSaga(state, ex.getMessage());
                }).then();
    }

    private Mono<Void> reservePayment(OrderSagaState state) {
        Order order = orderRepository.findById(state.getOrderId()).orElseThrow();
        return billingWebClient.post()
                .uri("/api/v1/payments/reserve")
                .bodyValue(new PaymentReserveRequest(
                        state.getOrderId().toString(),
                        order.getUserId().toString(),
                        order.getAmount().toPlainString(),
                        order.getCurrency()))
                .retrieve()
                .bodyToMono(PaymentReserveResponse.class)
                .timeout(STEP_TIMEOUT)
                .then();
    }

    private Mono<Void> reserveStock(OrderSagaState state, String itemSku, int quantity) {
        return warehouseWebClient.post()
                .uri("/api/v1/stock/reserve")
                .bodyValue(new StockReserveRequest(
                        state.getOrderId().toString(), itemSku, quantity))
                .retrieve()
                .bodyToMono(StockReserveResponse.class)
                .timeout(STEP_TIMEOUT)
                .then();
    }

    private Mono<Void> reserveDelivery(OrderSagaState state, String deliverySlot) {
        return deliveryWebClient.post()
                .uri("/api/v1/delivery/reserve")
                .bodyValue(new DeliveryReserveRequest(
                        state.getOrderId().toString(), deliverySlot))
                .retrieve()
                .bodyToMono(DeliveryReserveResponse.class)
                .timeout(STEP_TIMEOUT)
                .then();
    }

    private Mono<Void> confirmPayment(OrderSagaState state) {
        return billingWebClient.post()
                .uri("/api/v1/payments/confirm")
                .bodyValue(new PaymentConfirmRequest(state.getOrderId().toString()))
                .retrieve()
                .bodyToMono(Void.class)
                .timeout(STEP_TIMEOUT);
    }

    private void finalizeSaga(OrderSagaState state) {
        transactionExecutor.execInTransaction(() -> {
            state.setStatus(SagaStatus.CONFIRMED);
            sagaStateRepository.save(state);

            Order order = orderRepository.findById(state.getOrderId()).orElseThrow();
            order.setStatus(Order.OrderStatus.CONFIRMED);
            orderRepository.save(order);

            // Публикуем событие через outbox (для notification-service и аналитики)
            outboxService.saveEvent("OrderConfirmed", state.getOrderId(), "Order",
                    Map.of("orderId", state.getOrderId().toString(),
                            "userId", order.getUserId().toString(),
                            "amount", order.getAmount().toPlainString(),
                            "currency", order.getCurrency()));
        });
    }

    private Mono<Void> compensateSaga(OrderSagaState state, String reason) {
        state.setStatus(SagaStatus.COMPENSATING);
        sagaStateRepository.save(state);

        Mono<Void> compensation = Mono.empty();

        // Откатываем в ОБРАТНОМ порядке: Delivery → Stock → Payment
        if (state.isDeliveryReserved() || state.getStatus().ordinal() >= SagaStatus.DELIVERY_RESERVED.ordinal()) {
            compensation = compensation.then(cancelDelivery(state));
        }
        if (state.isStockReserved() || state.getStatus().ordinal() >= SagaStatus.STOCK_RESERVED.ordinal()) {
            compensation = compensation.then(cancelStock(state));
        }
        if (state.isPaymentReserved() || state.getStatus().ordinal() >= SagaStatus.PAYMENT_RESERVED.ordinal()) {
            compensation = compensation.then(cancelPayment(state));
        }

        return compensation.then(Mono.fromRunnable(() -> finalizeCancellation(state, reason)))
                .onErrorResume(ex -> {
                    log.error("Compensation failed for orderId={}: {}", state.getOrderId(), ex.getMessage());
                    // Если компенсация упала — помечаем для ручной обработки
                    state.setStatus(SagaStatus.FAILED);
                    sagaStateRepository.save(state);
                    return Mono.empty();
                }).then();
    }

    private Mono<Void> cancelDelivery(OrderSagaState state) {
        return deliveryWebClient.post()
                .uri("/api/v1/delivery/cancel")
                .bodyValue(new DeliveryCancelRequest(state.getOrderId().toString()))
                .retrieve()
                .bodyToMono(Void.class)
                .timeout(STEP_TIMEOUT)
                .onErrorResume(ex -> {
                    log.warn("Delivery cancel failed, will retry manually: {}", ex.getMessage());
                    return Mono.empty();
                });
    }

    private Mono<Void> cancelStock(OrderSagaState state) {
        return warehouseWebClient.post()
                .uri("/api/v1/stock/cancel")
                .bodyValue(new StockCancelRequest(state.getOrderId().toString()))
                .retrieve()
                .bodyToMono(Void.class)
                .timeout(STEP_TIMEOUT)
                .onErrorResume(ex -> {
                    log.warn("Stock cancel failed, will retry manually: {}", ex.getMessage());
                    return Mono.empty();
                });
    }

    private Mono<Void> cancelPayment(OrderSagaState state) {
        return billingWebClient.post()
                .uri("/api/v1/payments/cancel")
                .bodyValue(new PaymentCancelRequest(state.getOrderId().toString()))
                .retrieve()
                .bodyToMono(Void.class)
                .timeout(STEP_TIMEOUT)
                .onErrorResume(ex -> {
                    log.warn("Payment cancel failed, will retry manually: {}", ex.getMessage());
                    return Mono.empty();
                });
    }

    private void finalizeCancellation(OrderSagaState state, String reason) {
        transactionExecutor.execInTransaction(() -> {
            state.setStatus(SagaStatus.CANCELLED);
            sagaStateRepository.save(state);

            Order order = orderRepository.findById(state.getOrderId()).orElseThrow();
            order.setStatus(Order.OrderStatus.CANCELLED);
            orderRepository.save(order);

            outboxService.saveEvent("OrderCancelled", state.getOrderId(), "Order",
                    Map.of("orderId", state.getOrderId().toString(),
                            "userId", order.getUserId().toString(),
                            "amount", order.getAmount().toPlainString(),
                            "currency", order.getCurrency(),
                            "reason", reason));
        });
    }

    private void updateStatus(OrderSagaState state, SagaStatus newStatus) {
        state.setStatus(newStatus);
        switch (newStatus) {
            case PAYMENT_RESERVED -> state.setPaymentReserved(true);
            case STOCK_RESERVED -> state.setStockReserved(true);
            case DELIVERY_RESERVED -> state.setDeliveryReserved(true);
        }
        transactionExecutor.execInTransaction(() -> sagaStateRepository.save(state));
    }
}
