package ru.spavlochev.warehouse.dto;

import java.util.UUID;

public record StartPickingRequest(UUID orderId, String itemSku) {}
