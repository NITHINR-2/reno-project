package com.renault.fleet.catalog.dto;

import com.renault.fleet.catalog.entity.BodyType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateVehicleModelRequest(
        @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_-]{1,29}$", message = "2-30 chars: upper-case letters, digits, _ and -") String code,
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 40) @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "upper-case letters, digits and _") String vehicleCategory,
        @Size(max = 1000) String description,
        BodyType bodyType,
        @Min(1) @Max(60) Integer seatingCapacity,
        @Size(max = 500) @Pattern(regexp = "^https://\\S+$", message = "must be an https:// URL") String imageUrl) {
}
