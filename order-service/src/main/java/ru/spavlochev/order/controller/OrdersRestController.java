package ru.spavlochev.order.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.spavlochev.order.config.UserContext;
import ru.spavlochev.order.openapi.api.OrdersApi;
import ru.spavlochev.order.openapi.dto.CreateOrderRequestDto;
import ru.spavlochev.order.openapi.dto.OrderDto;
import ru.spavlochev.order.service.CreateOrderResult;
import ru.spavlochev.order.service.OrderService;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class OrdersRestController implements OrdersApi {

    private final OrderService orderService;

    @Override
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<OrderDto> createOrder(UUID idempotencyKey, CreateOrderRequestDto createOrderRequestDto) {
        UUID userId = UUID.fromString(UserContext.getCurrentUserId());
        CreateOrderResult result = orderService.createOrder(
                userId, createOrderRequestDto, idempotencyKey);
        return ResponseEntity.status(result.status())
                .body(result.order());
    }

    @Override
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<OrderDto> getOrder(UUID id) {
        UUID userId = UUID.fromString(UserContext.getCurrentUserId());
        OrderDto order = orderService.getOrder(userId, id);
        return ResponseEntity.ok(order);
    }

    @PostMapping("/api/v1/orders/{id}/cancel")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> cancelOrder(@PathVariable UUID id, @RequestParam String reason) {
        UUID userId = UUID.fromString(UserContext.getCurrentUserId());
        String jwtToken = UserContext.getJwt();
        orderService.cancelOrder(userId, id, reason, jwtToken);
        return ResponseEntity.noContent().build();
    }
}
