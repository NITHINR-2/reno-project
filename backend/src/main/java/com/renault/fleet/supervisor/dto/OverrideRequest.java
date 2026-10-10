package com.renault.fleet.supervisor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * Supervisor override of allow-listed fields. {@code changes} maps field name to the new value (JSON scalars;
 * money is parsed from the literal text, never via double). A null value clears an optional field.
 * Allowed: requiredDeposit, productionEstimateWeeks, requestedDeliveryDate (ISO date), plantId, notes.
 */
public record OverrideRequest(
        @NotBlank @Size(max = 1000) String reason,
        Long version,
        @NotNull @Size(min = 1, max = 5) Map<String, String> changes) {
}
