package ru.spavlochev.delivery.dto;

import java.util.UUID;

public record FailDeliveryRequest(UUID orderId, String trackingNumber, String reason) {}
