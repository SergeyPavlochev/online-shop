package ru.spavlochev.order.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import ru.spavlochev.onlineshop.events.v1.EventEnvelope;
import ru.spavlochev.order.dto.DeliveryCancelRequest;
import ru.spavlochev.order.dto.DeliveryReserveRequest;
import ru.spavlochev.order.dto.DeliveryReserveResponse;
import ru.spavlochev.order.dto.PaymentCancelRequest;
import ru.spavlochev.order.dto.PaymentConfirmRequest;
import ru.spavlochev.order.dto.PaymentRefundRequest;
import ru.spavlochev.order.dto.PaymentReserveRequest;
import ru.spavlochev.order.dto.PaymentReserveResponse;
import ru.spavlochev.order.dto.StockCancelRequest;
import ru.spavlochev.order.dto.StockReserveRequest;
import ru.spavlochev.order.dto.StockReserveResponse;
import ru.spavlochev.order.entity.Order;
import ru.spavlochev.order.entity.OrderSagaState;
import ru.spavlochev.order.entity.OrderSagaState.SagaStatus;
import ru.spavlochev.order.entity.ProcessedEvent;
import ru.spavlochev.order.repository.OrderRepository;
import ru.spavlochev.order.repository.OrderSagaStateRepository;
import ru.spavlochev.order.repository.ProcessedEventRepository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderSagaOrchestrator {

    private static final Duration STEP_TIMEOUT = Duration.ofSeconds(10);
    private static final String WAREHOUSE_EVENTS_TOPIC = "warehouse";
    private static final String DELIVERY_EVENTS_TOPIC = "delivery";

    private final OrderRepository orderRepository;
    private final OrderSagaStateRepository sagaStateRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final OutboxService outboxService;
    private final TransactionExecutor transactionExecutor;
    private final WebClient billingWebClient;
    private final WebClient warehouseWebClient;
    private final WebClient deliveryWebClient;
    private final WebClient m2mDeliveryWebClient;
    private final WebClient m2mWarehouseWebClient;
    private final WebClient m2mBillingWebClient;
    private final M2mTokenService m2mTokenService;

    // ==================== INITIALIZATION ====================

    /**
     * Запускает сагу для уже созданного заказа (из Outbox).
     * Используется M2M авторизация вместо JWT.
     */
    public void resumeOrderSaga(UUID orderId, String itemSku, int quantity, String deliverySlot) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + orderId));

        // Проверяем, что статус PENDING (сага ещё не началась)
        if (order.getStatus() != Order.OrderStatus.PENDING) {
            log.warn("Order {} already has status {}, skipping saga resume",
                    orderId, order.getStatus());
            return;
        }

        // Получаем или создаём saga state
        OrderSagaState state = sagaStateRepository.findById(orderId)
                .orElseGet(() -> sagaStateRepository.save(OrderSagaState.builder()
                        .orderId(orderId)
                        .status(SagaStatus.STARTED)
                        .build()));

        // Запускаем сагу с M2M авторизацией
        String m2mToken = m2mTokenService.getToken();
        executeSagaSteps(state, itemSku, quantity, deliverySlot, m2mToken)
                .subscribe();

        log.info("Saga resumed from outbox: orderId={}", orderId);
    }

    private Mono<Void> executeSagaSteps(
            OrderSagaState state, String itemSku, int quantity, String deliverySlot, String jwtToken) {
        return reservePayment(state, jwtToken)
                .then(Mono.fromRunnable(() -> updateSagaState(state, SagaStatus.PAYMENT_RESERVED)))
                .then(reserveStock(state, itemSku, quantity, jwtToken))
                .then(Mono.fromRunnable(() -> updateSagaState(state, SagaStatus.STOCK_RESERVED)))
                .then(reserveDelivery(state, deliverySlot, jwtToken))
                .then(Mono.fromRunnable(() -> updateSagaState(state, SagaStatus.DELIVERY_RESERVED)))
                .then(confirmPayment(state, jwtToken))
                .then(Mono.fromRunnable(() -> finalizeSaga(state)))
                .onErrorResume(ex -> {
                    log.error("Saga failed at step {}: {}", state.getStatus(), ex.getMessage());
                    return compensateSaga(state, jwtToken, ex.getMessage());
                }).then();
    }

    // ==================== PRE-FULFILLMENT ====================

    private Mono<Void> reservePayment(OrderSagaState state, String jwtToken) {
        Order order = orderRepository.findById(state.getOrderId())
                .orElseThrow(() -> new EntityNotFoundException("Order not found for id: " + state.getOrderId()));
        PaymentReserveRequest request = new PaymentReserveRequest(
                state.getOrderId().toString(),
                order.getUserId().toString(),
                order.getAmount().toPlainString(),
                order.getCurrency());

        return billingWebClient.post()
                .uri("/api/v1/payments/reserve")
                .headers(headers -> headers.setBearerAuth(jwtToken))
                .bodyValue(request)
                .retrieve()
                .bodyToMono(PaymentReserveResponse.class)
                .timeout(STEP_TIMEOUT)
                .then();
    }

    private Mono<Void> reserveStock(OrderSagaState state, String itemSku, int quantity, String jwtToken) {
        return warehouseWebClient.post()
                .uri("/api/v1/stock/reserve")
                .headers(headers -> headers.setBearerAuth(jwtToken))
                .bodyValue(new StockReserveRequest(
                        state.getOrderId().toString(), itemSku, quantity))
                .retrieve()
                .bodyToMono(StockReserveResponse.class)
                .timeout(STEP_TIMEOUT)
                .then();
    }

    private Mono<Void> reserveDelivery(OrderSagaState state, String deliverySlot, String jwtToken) {
        return deliveryWebClient.post()
                .uri("/api/v1/delivery/reserve")
                .headers(headers -> headers.setBearerAuth(jwtToken))
                .bodyValue(new DeliveryReserveRequest(
                        state.getOrderId().toString(), deliverySlot))
                .retrieve()
                .bodyToMono(DeliveryReserveResponse.class)
                .timeout(STEP_TIMEOUT)
                .then();
    }

    private Mono<Void> confirmPayment(OrderSagaState state, String jwtToken) {
        return billingWebClient.post()
                .uri("/api/v1/payments/confirm")
                .headers(headers -> headers.setBearerAuth(jwtToken))
                .bodyValue(new PaymentConfirmRequest(state.getOrderId().toString()))
                .retrieve()
                .bodyToMono(Void.class)
                .timeout(STEP_TIMEOUT)
                .then();
    }

    private void finalizeSaga(OrderSagaState state) {
        transactionExecutor.execInTransaction(() -> {
            updateSagaState(state, SagaStatus.CONFIRMED);

            Order order = orderRepository.findById(state.getOrderId()).orElseThrow();
            order.setStatus(Order.OrderStatus.PAYMENT_COMPLETED);
            orderRepository.save(order);
            publishOrderPaymentCompleted(order);
        });
    }

    // ==================== FULFILLMENT (EVENT-DRIVEN) ====================

    @KafkaListener(topics = WAREHOUSE_EVENTS_TOPIC, groupId = "${spring.kafka.consumer.group-id}")
    public void handleWarehouseEvent(EventEnvelope envelope) {
        String orderId = envelope.getCorrelationId();
        log.info("Received warehouse event: type={}, orderId={}", envelope.getEventType(), orderId);

        processEventIdempotently(envelope, () -> {
            OrderSagaState state = sagaStateRepository.findById(UUID.fromString(orderId)).orElse(null);
            if (state == null) {
                log.warn("Saga state not found for orderId={}", orderId);
                return;
            }

            if (envelope.hasOrderPickingStarted()) {
                handlePickingStarted(state);
            } else if (envelope.hasOrderPickingCompleted()) {
                handlePickingCompleted(state, envelope);
            } else if (envelope.hasOrderPickingFailed()) {
                handlePickingFailed(state, envelope);
            }
        });
    }

    @KafkaListener(topics = DELIVERY_EVENTS_TOPIC, groupId = "${spring.kafka.consumer.group-id}")
    public void handleDeliveryEvent(EventEnvelope envelope) {
        String orderId = envelope.getCorrelationId();
        log.info("Received delivery event: type={}, orderId={}", envelope.getEventType(), orderId);

        processEventIdempotently(envelope, () -> {
            OrderSagaState state = sagaStateRepository.findById(UUID.fromString(orderId)).orElse(null);
            if (state == null) {
                log.warn("Saga state not found for orderId={}", orderId);
                return;
            }

            if (envelope.hasOrderDeliveryStarted()) {
                handleDeliveryStarted(state, envelope);
            } else if (envelope.hasOrderDelivered()) {
                handleDelivered(state, envelope);
            } else if (envelope.hasOrderDeliveryFailed()) {
                handleDeliveryFailed(state, envelope);
            }
        });
    }

    private void handlePickingStarted(OrderSagaState state) {
        state.setStatus(SagaStatus.PICKING);
        state.setPickingStartedAt(LocalDateTime.now());
        sagaStateRepository.save(state);

        Order order = orderRepository.findById(state.getOrderId()).orElseThrow();
        order.setStatus(Order.OrderStatus.PICKING);
        orderRepository.save(order);

        log.info("Order picking started: orderId={}", state.getOrderId());
    }

    private void handlePickingCompleted(OrderSagaState state, EventEnvelope envelope) {
        var event = envelope.getOrderPickingCompleted();
        state.setPickingCompleted(true);
        state.setTrackingNumber(event.getTrackingNumber());
        state.setStatus(SagaStatus.READY_FOR_DELIVERY);
        sagaStateRepository.save(state);

        Order order = orderRepository.findById(state.getOrderId()).orElseThrow();
        order.setTrackingNumber(event.getTrackingNumber());
        order.setStatus(Order.OrderStatus.READY_FOR_DELIVERY);
        orderRepository.save(order);

        log.info("Picking completed, ready for delivery: orderId={}, tracking={}",
                state.getOrderId(), event.getTrackingNumber());
    }

    private void handlePickingFailed(OrderSagaState state, EventEnvelope envelope) {
        var event = envelope.getOrderPickingFailed();
        log.warn("Picking failed for orderId={}, reason={}", state.getOrderId(), event.getReason());

        state.setFailureReason(event.getReason());
        sagaStateRepository.save(state);

        compensateOnStepFailed(state, "Picking", event.getReason());
    }

    private void compensateOnStepFailed(OrderSagaState state, String stepName, String reason) {
        if (isTerminalState(state.getStatus())) {
            log.info("Compensation skipped: order {} already in terminal state {}",
                    state.getOrderId(), state.getStatus());
            return;
        }

        state.setStatus(SagaStatus.COMPENSATING);
        sagaStateRepository.save(state);

        try {
            cancelDeliveryInternal(state);
            cancelStockInternal(state);
            refundPaymentInternal(state);
            finalizeCancellation(state, stepName + " failed: " + reason);
        } catch (Exception ex) {
            log.error("Compensation failed for orderId={}: {}", state.getOrderId(), ex.getMessage(), ex);
            state.setStatus(SagaStatus.FAILED);
            sagaStateRepository.save(state);
        }
    }

    private void handleDeliveryStarted(OrderSagaState state, EventEnvelope envelope) {
        var event = envelope.getOrderDeliveryStarted();
        state.setTrackingNumber(event.getTrackingNumber());
        state.setActiveCourierId(UUID.fromString(event.getCourierId()));
        state.setStatus(SagaStatus.IN_DELIVERY);
        sagaStateRepository.save(state);

        Order order = orderRepository.findById(state.getOrderId()).orElseThrow();
        order.setTrackingNumber(event.getTrackingNumber());
        order.setStatus(Order.OrderStatus.IN_DELIVERY);
        orderRepository.save(order);

        log.info("Delivery started: orderId={}, tracking={}, courier={}",
                state.getOrderId(), event.getTrackingNumber(), event.getCourierId());
    }

    private void handleDelivered(OrderSagaState state, EventEnvelope envelope) {
        var event = envelope.getOrderDelivered();
        state.setStatus(SagaStatus.DELIVERED);
        state.setDeliveredAt(LocalDateTime.now());
        sagaStateRepository.save(state);

        Order order = orderRepository.findById(state.getOrderId()).orElseThrow();
        order.setDeliveredAt(LocalDateTime.now());
        order.setStatus(Order.OrderStatus.DELIVERED);
        orderRepository.save(order);

        outboxService.saveEvent("OrderDelivered", state.getOrderId(), "Order",
                Map.of("orderId", state.getOrderId().toString(),
                        "userId", order.getUserId().toString(),
                        "trackingNumber", event.getTrackingNumber()));

        log.info("Order delivered: orderId={}, tracking={}", state.getOrderId(), event.getTrackingNumber());
    }

    private void handleDeliveryFailed(OrderSagaState state, EventEnvelope envelope) {
        var event = envelope.getOrderDeliveryFailed();
        log.warn("Delivery failed for orderId={}, reason={}", state.getOrderId(), event.getReason());

        state.setFailureReason(event.getReason());
        sagaStateRepository.save(state);

        compensateOnStepFailed(state, "Delivery", event.getReason());
    }

    // ==================== CANCELLATION ====================

    @Transactional
    public void cancelOrder(UUID orderId, String reason, String jwtToken) {
        OrderSagaState state = sagaStateRepository.findById(orderId).orElseThrow();
        orderRepository.findById(orderId).orElseThrow();

        if (isTerminalState(state.getStatus())) {
            log.warn("Order {} is already in terminal state: {}", orderId, state.getStatus());
            return;
        }

        log.info("Cancelling order: orderId={}, reason={}", orderId, reason);

        if (state.getStatus().ordinal() <= SagaStatus.CONFIRMED.ordinal()) {
            compensateSaga(state, reason, jwtToken).block();
        } else {
            compensateFulfillment(state, reason);
        }
    }

    private void compensateFulfillment(OrderSagaState state, String reason) {
        if (isTerminalState(state.getStatus())) {
            log.info("Compensation skipped: order {} already in terminal state {}",
                    state.getOrderId(), state.getStatus());
            return;
        }

        state.setStatus(SagaStatus.COMPENSATING);
        sagaStateRepository.save(state);

        try {
            if (state.isDeliveryReserved() || state.getStatus().ordinal() >= SagaStatus.IN_DELIVERY.ordinal()) {
                cancelDeliveryInternal(state);
            }
            if (state.isStockReserved() || state.getStatus().ordinal() >= SagaStatus.PICKING.ordinal()) {
                cancelStockInternal(state);
            }
            refundPaymentInternal(state);
            finalizeCancellation(state, reason);
        } catch (Exception ex) {
            log.error("Fulfillment compensation failed for orderId={}: {}",
                    state.getOrderId(), ex.getMessage(), ex);
            state.setStatus(SagaStatus.FAILED);
            sagaStateRepository.save(state);
            return;
        }

        finalizeCancellation(state, reason);
    }

    private Mono<Void> compensateSaga(OrderSagaState state, String reason, String jwtToken) {
        if (isTerminalState(state.getStatus())) {
            log.info("Compensation skipped: order {} already in terminal state {}",
                    state.getOrderId(), state.getStatus());
            return Mono.empty();
        }

        state.setStatus(SagaStatus.COMPENSATING);
        sagaStateRepository.save(state);

        Mono<Void> compensation = Mono.empty();

        try {
            // Откатываем в ОБРАТНОМ порядке: Delivery → Stock → Payment
            if (state.isDeliveryReserved() || state.getStatus().ordinal() >= SagaStatus.DELIVERY_RESERVED.ordinal()) {
                compensation = compensation.then(cancelDelivery(state, jwtToken));
            }
            if (state.isStockReserved() || state.getStatus().ordinal() >= SagaStatus.STOCK_RESERVED.ordinal()) {
                compensation = compensation.then(cancelStock(state, jwtToken));
            }
            if (state.isPaymentReserved() || state.getStatus().ordinal() >= SagaStatus.PAYMENT_RESERVED.ordinal()) {
                compensation = compensation.then(cancelPayment(state, jwtToken));
            }

            return compensation.then(Mono.fromRunnable(() -> finalizeCancellation(state, reason)))
                    .onErrorResume(ex -> {
                        log.error("Compensation failed for orderId={}: {}", state.getOrderId(), ex.getMessage());
                        // Если компенсация упала — помечаем для ручной обработки
                        state.setStatus(SagaStatus.FAILED);
                        sagaStateRepository.save(state);
                        return Mono.empty();
                    }).then();
        } catch (Exception ex) {
            log.error("Compensation failed for orderId={}: {}", state.getOrderId(), ex.getMessage(), ex);
            state.setStatus(SagaStatus.FAILED);
            sagaStateRepository.save(state);
            return Mono.empty();
        }
    }

    // ==================== COMPENSATION HTTP ====================

    private Mono<Void> cancelDelivery(OrderSagaState state, String jwtToken) {
        return deliveryWebClient.post()
                .uri("/api/v1/delivery/cancel")
                .headers(headers -> headers.setBearerAuth(jwtToken))
                .bodyValue(new DeliveryCancelRequest(state.getOrderId().toString()))
                .retrieve()
                .bodyToMono(Void.class)
                .timeout(STEP_TIMEOUT)
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                        .maxBackoff(Duration.ofSeconds(4))
                        .jitter(0.5)
                        .filter(this::isRetryable))
                .onErrorResume(ex -> {
                    log.warn("Delivery cancel failed after retries for orderId={}: {}",
                            state.getOrderId(), ex.getMessage());
                    return Mono.empty();
                });
    }

    private Mono<Void> cancelStock(OrderSagaState state, String jwtToken) {
        return warehouseWebClient.post()
                .uri("/api/v1/stock/cancel")
                .headers(headers -> headers.setBearerAuth(jwtToken))
                .bodyValue(new StockCancelRequest(state.getOrderId().toString()))
                .retrieve()
                .bodyToMono(Void.class)
                .timeout(STEP_TIMEOUT)
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                        .maxBackoff(Duration.ofSeconds(4))
                        .jitter(0.5)
                        .filter(this::isRetryable))
                .onErrorResume(ex -> {
                    log.warn("Stock cancel failed after retries for orderId={}: {}",
                            state.getOrderId(), ex.getMessage());
                    return Mono.empty();
                });
    }

    private Mono<Void> cancelPayment(OrderSagaState state, String jwtToken) {
        return billingWebClient.post()
                .uri("/api/v1/payments/cancel")
                .headers(headers -> headers.setBearerAuth(jwtToken))
                .bodyValue(new PaymentCancelRequest(state.getOrderId().toString()))
                .retrieve()
                .bodyToMono(Void.class)
                .timeout(STEP_TIMEOUT)
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                        .maxBackoff(Duration.ofSeconds(4))
                        .jitter(0.5)
                        .filter(this::isRetryable))
                .onErrorResume(ex -> {
                    log.warn("Payment cancel failed after retries for orderId={}: {}",
                            state.getOrderId(), ex.getMessage());
                    return Mono.empty();
                });
    }

    // Internal methods for Kafka-based compensation (M2M token via client_credentials)
    private void cancelDeliveryInternal(OrderSagaState state) {
        executeM2mRequest(m2mDeliveryWebClient, "/api/v1/delivery/cancel",
                new DeliveryCancelRequest(state.getOrderId().toString()),
                "Delivery cancel");
    }

    private void cancelStockInternal(OrderSagaState state) {
        executeM2mRequest(m2mWarehouseWebClient, "/api/v1/stock/cancel",
                new StockCancelRequest(state.getOrderId().toString()),
                "Stock cancel");
    }

    private void refundPaymentInternal(OrderSagaState state) {
        executeM2mRequest(m2mBillingWebClient, "/api/v1/payments/refund",
                new PaymentRefundRequest(state.getOrderId()),
                "Payment refund");
    }

    private void executeM2mRequest(WebClient client, String uri, Object request, String logPrefix) {
        String token = m2mTokenService.getToken();
        client.post()
                .uri(uri)
                .headers(headers -> headers.setBearerAuth(token))
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Void.class)
                .timeout(STEP_TIMEOUT)
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                        .maxBackoff(Duration.ofSeconds(4))
                        .jitter(0.5)
                        .filter(this::isRetryable))
                .onErrorResume(ex -> {
                    log.warn("{} failed after retries: {}", logPrefix, ex.getMessage());
                    return Mono.empty();
                })
                .block();
    }

    // ==================== HELPERS ====================

    private boolean isRetryable(Throwable ex) {
        return ex instanceof WebClientResponseException e
                && e.getStatusCode().is5xxServerError()
                || ex instanceof java.io.IOException;
    }

    private void updateSagaState(OrderSagaState state, SagaStatus newStatus) {
        transactionExecutor.execInTransaction(() -> {
            OrderSagaState managedState = sagaStateRepository.findById(state.getOrderId())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Saga state not found for orderId: " + state.getOrderId()));
            managedState.setStatus(newStatus);
            switch (newStatus) {
                case PAYMENT_RESERVED -> managedState.setPaymentReserved(true);
                case STOCK_RESERVED -> managedState.setStockReserved(true);
                case DELIVERY_RESERVED -> managedState.setDeliveryReserved(true);
            }
        });
    }

    private void publishOrderPaymentCompleted(Order order) {
        outboxService.saveEvent("OrderPaymentCompleted", order.getId(), "Order",
                Map.of("orderId", order.getId().toString(),
                        "userId", order.getUserId().toString(),
                        "amount", order.getAmount().toPlainString(),
                        "currency", order.getCurrency()));
    }

    private void processEventIdempotently(EventEnvelope envelope, Runnable action) {
        UUID eventId = UUID.fromString(envelope.getEventId());

        transactionExecutor.execInTransaction(() -> {
            if (processedEventRepository.existsByEventId(eventId)) {
                log.info("Event already processed, skipping: eventId={}, type={}",
                        eventId, envelope.getEventType());
                return;
            }

            log.info("Processing event: eventId={}, type={}", eventId, envelope.getEventType());
            action.run();
            processedEventRepository.save(ProcessedEvent.builder()
                    .eventId(eventId)
                    .eventType(envelope.getEventType())
                    .build());
            log.info("Event processed successfully: eventId={}", eventId);
        });
    }

    private boolean isTerminalState(SagaStatus status) {
        return status == SagaStatus.DELIVERED
                || status == SagaStatus.CANCELLED
                || status == SagaStatus.FAILED;
    }

    private void finalizeCancellation(OrderSagaState state, String reason) {
        transactionExecutor.execInTransaction(() -> {
            state.setStatus(SagaStatus.CANCELLED);
            state.setUpdatedAt(LocalDateTime.now());
            sagaStateRepository.save(state);

            Order order = orderRepository.findById(state.getOrderId()).orElseThrow();
            order.setStatus(Order.OrderStatus.CANCELLED);
            order.setCancelledAt(LocalDateTime.now());
            orderRepository.save(order);

            outboxService.saveEvent("OrderCancelled", state.getOrderId(), "Order",
                    Map.of("orderId", state.getOrderId().toString(),
                            "userId", order.getUserId().toString(),
                            "amount", order.getAmount().toPlainString(),
                            "currency", order.getCurrency(),
                            "reason", reason));

            log.info("Order cancelled: orderId={}, reason={}", state.getOrderId(), reason);
        });
    }
}
