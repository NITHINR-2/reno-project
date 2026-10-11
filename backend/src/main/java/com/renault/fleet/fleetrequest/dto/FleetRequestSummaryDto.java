package com.renault.fleet.fleetrequest.dto;

import com.renault.fleet.fleetrequest.entity.FleetRequestStatus;
import com.renault.fleet.sla.entity.SlaStatus;
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
        Instant stageEnteredAt,
        Instant slaDueAt,
        SlaStatus slaStatus,
        Long version,
        Instant createdAt,
        Instant updatedAt) {
}
