package ru.spavlochev.delivery.dto;

import java.util.UUID;

public record DeliverRequest(UUID orderId, String trackingNumber) {}
