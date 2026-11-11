package ru.spavlochev.order.service;

import org.springframework.http.HttpStatus;
import ru.spavlochev.order.openapi.dto.OrderDto;

public record CreateOrderResult(HttpStatus status, OrderDto order) {
}
