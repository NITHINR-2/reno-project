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

/**
 * Each line becomes one fleet request; a (variant, colour) pair may appear only once per order. With a {@code plantId}
 * each line reserves min(quantity, available) at that plant and the rest is produced; without one, nothing is reserved
 * and Marketing maps stock as for HQ-raised requests.
 */
public record CreateDealerOrderRequest(
        @NotEmpty @Size(max = 20) List<@Valid @NotNull Line> lines,
        Long plantId,
        @Future LocalDate requestedDeliveryDate,
        @Size(max = 2000) String notes) {

    /**
     * {@code colourId} is optional; when given it must be an active colour of the variant's model.
     * {@code expectedFromStock} is what the dealer was shown as coming from the plant's stock; if fewer are available
     * now the order is refused with 409 and the current count, so the dealer decides instead of silently getting less.
     */
    public record Line(@NotNull Long variantId, @NotNull @Min(1) @Max(10_000) Integer quantity, Long colourId,
            @Min(0) @Max(10_000) Integer expectedFromStock) {
    }
}
