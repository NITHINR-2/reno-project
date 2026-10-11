package com.renault.fleet.catalog.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Partial update: a null field means "leave unchanged". */
public record UpdateColourRequest(
        @NotNull Long version,
        @Size(min = 1, max = 50) String name,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "must be #RRGGBB") String hexCode,
        Boolean active) {
}
