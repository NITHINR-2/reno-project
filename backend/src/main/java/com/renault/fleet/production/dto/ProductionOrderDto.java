package com.renault.fleet.production.dto;

import com.renault.fleet.production.entity.ProductionStatus;
import java.time.Instant;
import java.time.LocalDate;

public record ProductionOrderDto(
        Long id,
        String productionNumber,
        Long plantId,
        String plantName,
        int quantity,
        LocalDate scheduledStart,
        LocalDate estimatedCompletion,
        Instant completedAt,
        ProductionStatus status,
        String scheduledBy,
        String notes,
        Instant createdAt) {
}
