package com.renault.fleet.audit.dto;

import java.time.Instant;
import tools.jackson.databind.JsonNode;

public record AuditLogDto(
        Long id,
        Long actorId,
        String actorUsername,
        String actorRole,
        String action,
        String entityType,
        String entityId,
        JsonNode before,
        JsonNode after,
        String reason,
        String ipAddress,
        Instant createdAt) {
}
