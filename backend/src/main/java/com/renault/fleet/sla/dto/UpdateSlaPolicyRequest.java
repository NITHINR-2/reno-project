package com.renault.fleet.sla.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Partial update; null leaves a field unchanged. {@code version} is required (optimistic locking). */
public record UpdateSlaPolicyRequest(
        @NotNull Long version,
        @Min(1) @Max(8760) Integer hoursAllowed,
        @Min(1) @Max(99) Integer warningThresholdPct,
        Boolean active) {
}
