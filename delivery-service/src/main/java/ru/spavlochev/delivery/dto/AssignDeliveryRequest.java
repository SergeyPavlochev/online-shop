package ru.spavlochev.delivery.dto;

import java.util.UUID;

public record AssignDeliveryRequest(UUID orderId, UUID courierId, String trackingNumber) {}
