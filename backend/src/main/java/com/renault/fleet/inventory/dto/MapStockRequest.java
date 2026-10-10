package com.renault.fleet.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Replaces the request's active stock allocations with {@code lines}. An empty list records that no stock is
 * used (everything is produced). {@code version} is the fleet request's version, if the client wants a stale check.
 */
public record MapStockRequest(
        Long version,
        @NotNull @Size(max = 50) List<@Valid @NotNull Line> lines) {

    public record Line(@NotNull Long stockId, @NotNull @Positive Integer quantity) {
    }
}
