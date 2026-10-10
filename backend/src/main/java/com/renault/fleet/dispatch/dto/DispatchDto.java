package com.renault.fleet.dispatch.dto;

import com.renault.fleet.dispatch.entity.DispatchStatus;
import java.time.Instant;
import java.time.LocalDate;

public record DispatchDto(
        Long id,
        String dispatchNumber,
        Long fromLocationId,
        String fromLocationName,
        int quantity,
        LocalDate scheduledDate,
        Instant dispatchedAt,
        Instant deliveredAt,
        String carrier,
        String vehicleReference,
        DispatchStatus status,
        String scheduledBy,
        String notes,
        Instant createdAt) {
}
