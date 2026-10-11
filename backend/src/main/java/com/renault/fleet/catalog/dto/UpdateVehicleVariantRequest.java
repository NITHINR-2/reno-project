package com.renault.fleet.catalog.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Partial update. A price change applies to quotations calculated afterwards; issued ones are frozen. */
public record UpdateVehicleVariantRequest(
        @NotNull Long version,
        @Size(min = 1, max = 100) String name,
        @DecimalMin("0.01") @Digits(integer = 13, fraction = 2) BigDecimal unitPrice,
        Boolean active) {
}
