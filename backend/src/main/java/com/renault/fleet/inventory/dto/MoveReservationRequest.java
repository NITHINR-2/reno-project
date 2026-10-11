package com.renault.fleet.inventory.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Move a request's stock reservation to another plant; the reason goes to history and the audit log. */
public record MoveReservationRequest(@NotNull Long plantId, @NotNull Long version, @Size(max = 1000) String reason) {
}
