package com.renault.fleet.fleetrequest.dto;

import com.renault.fleet.fleetrequest.entity.FleetRequestStatus;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;

/** Query-string filters for {@code GET /api/fleet-requests}; every field is optional. */
public record FleetRequestFilter(
        List<FleetRequestStatus> status,
        Long dealerId,
        Long regionId,
        Long variantId,
        Long modelId,
        Long plantId,
        String q,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo) {
}
