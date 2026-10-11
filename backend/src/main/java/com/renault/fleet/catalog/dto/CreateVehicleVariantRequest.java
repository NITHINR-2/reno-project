package com.renault.fleet.catalog.dto;

import com.renault.fleet.catalog.entity.FuelType;
import com.renault.fleet.catalog.entity.Transmission;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateVehicleVariantRequest(
        @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_-]{1,39}$", message = "2-40 chars: upper-case letters, digits, _ and -") String code,
        @NotBlank @Size(max = 100) String name,
        @NotNull FuelType fuelType,
        Transmission transmission,
        @NotNull @DecimalMin("0.01") @Digits(integer = 13, fraction = 2) BigDecimal unitPrice) {
}
