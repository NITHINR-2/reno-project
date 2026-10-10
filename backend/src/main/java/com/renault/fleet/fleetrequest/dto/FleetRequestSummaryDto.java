package com.renault.fleet.fleetrequest.dto;

import com.renault.fleet.fleetrequest.entity.FleetRequestStatus;
import java.time.Instant;

public record FleetRequestSummaryDto(
        Long id,
        String requestNumber,
        String dealerName,
        String variantName,
        int quantity,
        int allocatedQty,
        int productionRequired,
        FleetRequestStatus status,
        Instant createdAt,
        Instant updatedAt) {
}
