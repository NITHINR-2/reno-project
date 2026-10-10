package com.renault.fleet.fleetrequest.dto;

import com.renault.fleet.fleetrequest.entity.FleetRequestStatus;
import java.time.Instant;
import java.time.LocalDate;

public record FleetRequestSummaryDto(
        Long id,
        String requestNumber,
        Long dealerId,
        String dealerName,
        String regionName,
        String modelName,
        String variantName,
        String plantName,
        int quantity,
        int allocatedQty,
        int productionRequired,
        LocalDate requestedDeliveryDate,
        FleetRequestStatus status,
        Long version,
        Instant createdAt,
        Instant updatedAt) {
}
