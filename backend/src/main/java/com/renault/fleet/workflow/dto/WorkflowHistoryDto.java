package com.renault.fleet.workflow.dto;

import com.renault.fleet.fleetrequest.entity.FleetRequestStatus;
import java.time.Instant;
import tools.jackson.databind.JsonNode;

/** One timeline entry. {@code actorId}/{@code actorName} are null for system events. */
public record WorkflowHistoryDto(
        Long id,
        FleetRequestStatus fromStatus,
        FleetRequestStatus toStatus,
        String action,
        Long actorId,
        String actorName,
        String actorRole,
        String reason,
        boolean override,
        JsonNode metadata,
        Instant createdAt) {
}
