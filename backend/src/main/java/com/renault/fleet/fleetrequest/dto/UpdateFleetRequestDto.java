package com.renault.fleet.fleetrequest.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Partial update: a null field means "leave unchanged". {@code version} is required (optimistic locking). */
public record UpdateFleetRequestDto(
        @NotNull Long version,
        Long variantId,
        Long plantId,
        @Min(1) @Max(10_000) Integer quantity,
        @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal requiredDeposit,
        @Min(0) @Max(104) Integer productionEstimateWeeks,
        @Future LocalDate requestedDeliveryDate,
        @Size(max = 4000) String notes) {
}
