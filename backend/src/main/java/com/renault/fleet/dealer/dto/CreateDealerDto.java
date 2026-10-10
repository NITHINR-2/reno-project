package com.renault.fleet.dealer.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** New dealers start in PENDING_ONBOARDING. */
public record CreateDealerDto(
        @NotBlank @Size(max = 30) @Pattern(regexp = DealerPatterns.CODE, message = DealerPatterns.CODE_MESSAGE) String dealerCode,
        @NotBlank @Size(max = 150) String name,
        @NotNull Long regionId,
        @Pattern(regexp = DealerPatterns.GSTIN, message = DealerPatterns.GSTIN_MESSAGE) String gstin,
        @Size(max = 150) String contactName,
        @Email @Size(max = 255) String contactEmail,
        @Pattern(regexp = DealerPatterns.PHONE, message = DealerPatterns.PHONE_MESSAGE) String contactPhone,
        @Size(max = 255) String addressLine,
        @NotBlank @Size(max = 100) String city,
        @NotBlank @Size(max = 100) String state,
        @Pattern(regexp = DealerPatterns.PIN, message = DealerPatterns.PIN_MESSAGE) String postalCode) {
}
