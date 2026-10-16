package ru.spavlochev.order.dto;

public record StockReserveRequest(String orderId,
                                  String itemSku,
                                  Integer quantity) {
}
