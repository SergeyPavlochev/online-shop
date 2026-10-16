package ru.spavlochev.delivery.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record AvailableSlotDto(UUID id,
                               LocalDateTime startTime,
                               LocalDateTime endTime,
                               String zone,
                               Integer availablePlaces) {
}
