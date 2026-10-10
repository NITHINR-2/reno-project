package com.renault.fleet.dealer.dto;

import com.renault.fleet.dealer.entity.DealerStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Partial update: a null field means "leave unchanged". {@code version} is required (optimistic locking). */
public record UpdateDealerDto(
        @NotNull Long version,
        @Size(min = 1, max = 150) String name,
        Long regionId,
        @Pattern(regexp = DealerPatterns.GSTIN, message = DealerPatterns.GSTIN_MESSAGE) String gstin,
        @Size(max = 150) String contactName,
        @Email @Size(max = 255) String contactEmail,
        @Pattern(regexp = DealerPatterns.PHONE, message = DealerPatterns.PHONE_MESSAGE) String contactPhone,
        @Size(max = 255) String addressLine,
        @Size(min = 1, max = 100) String city,
        @Size(min = 1, max = 100) String state,
        @Pattern(regexp = DealerPatterns.PIN, message = DealerPatterns.PIN_MESSAGE) String postalCode,
        DealerStatus status) {
}
