package ru.spavlochev.order.dto;

public record DeliveryReserveRequest(String orderId,
                                     String slotId) {
}
