package com.renault.fleet.dealer.dto;

import com.renault.fleet.dealer.entity.DealerStatus;
import java.time.Instant;

public record DealerDto(
        Long id,
        String dealerCode,
        String name,
        Long regionId,
        String regionName,
        String gstin,
        String contactName,
        String contactEmail,
        String contactPhone,
        String addressLine,
        String city,
        String state,
        String postalCode,
        DealerStatus status,
        Instant onboardedAt,
        Long version,
        Instant createdAt,
        Instant updatedAt) {
}
