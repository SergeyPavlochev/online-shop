package ru.spavlochev.order.dto;

public record PaymentReserveRequest(String orderId,
                                    String userId,
                                    String amount,
                                    String currency) {
}
