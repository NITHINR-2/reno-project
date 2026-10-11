package com.renault.fleet.dealerportal.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/** Each line becomes one fleet request; a variant may appear only once per order. */
public record CreateDealerOrderRequest(
        @NotEmpty @Size(max = 20) List<@Valid @NotNull Line> lines,
        @Future LocalDate requestedDeliveryDate,
        @Size(max = 2000) String notes) {

    public record Line(@NotNull Long variantId, @NotNull @Min(1) @Max(10_000) Integer quantity) {
    }
}
