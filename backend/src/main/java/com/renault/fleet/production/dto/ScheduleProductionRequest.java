package com.renault.fleet.production.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** {@code plantId} defaults to the request's plant. The order quantity is always the request's productionRequired. */
public record ScheduleProductionRequest(
        Long plantId,
        @NotNull LocalDate scheduledStart,
        @NotNull LocalDate estimatedCompletion,
        @Size(max = 1000) String notes,
        Long version) {
}
