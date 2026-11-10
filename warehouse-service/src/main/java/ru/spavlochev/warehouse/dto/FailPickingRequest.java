package ru.spavlochev.warehouse.dto;

import java.util.UUID;

public record FailPickingRequest(UUID orderId, String reason) {}
