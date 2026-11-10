package ru.spavlochev.order.dto;

import java.util.UUID;

public class PaymentRefundRequest {
    private UUID orderId;

    public PaymentRefundRequest() {
    }

    public PaymentRefundRequest(UUID orderId) {
        this.orderId = orderId;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public void setOrderId(UUID orderId) {
        this.orderId = orderId;
    }
}
