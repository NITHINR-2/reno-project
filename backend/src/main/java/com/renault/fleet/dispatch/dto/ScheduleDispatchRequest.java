package com.renault.fleet.dispatch.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** The dispatch quantity is always the full request quantity. */
public record ScheduleDispatchRequest(
        @NotNull LocalDate scheduledDate,
        Long fromLocationId,
        @Size(max = 150) String carrier,
        @Size(max = 100) String vehicleReference,
        @Size(max = 1000) String notes,
        Long version) {
}
