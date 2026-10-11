package com.renault.fleet.catalog.dto;

import com.renault.fleet.catalog.entity.BodyType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Partial update: a null field means "leave unchanged". {@code active=false} hides the model from dealers. */
public record UpdateVehicleModelRequest(
        @NotNull Long version,
        @Size(min = 1, max = 100) String name,
        @Size(max = 40) @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "upper-case letters, digits and _") String vehicleCategory,
        @Size(max = 1000) String description,
        BodyType bodyType,
        @Min(1) @Max(60) Integer seatingCapacity,
        @Size(max = 500) @Pattern(regexp = "^https://\\S+$", message = "must be an https:// URL") String imageUrl,
        Boolean active) {
}
