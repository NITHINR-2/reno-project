package com.renault.fleet.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ColourRequest(
        @NotBlank @Size(max = 50) String name,
        @NotBlank @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "must be #RRGGBB") String hexCode) {
}
