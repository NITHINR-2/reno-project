package com.renault.fleet.sla.dto;

import com.renault.fleet.fleetrequest.entity.FleetRequestStatus;
import java.time.Instant;

public record SlaPolicyDto(Long id, FleetRequestStatus stage, int hoursAllowed, int warningThresholdPct, boolean active,
        Long version, Instant updatedAt) {
}
