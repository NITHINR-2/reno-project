package com.renault.fleet.audit.service;

import com.renault.fleet.security.AuthenticatedUser;

/**
 * What to write to {@code audit_logs}. Build with {@link #of(String, String, String)} and the {@code with*} methods.
 * JSON fields hold already-serialised JSON (or null).
 */
public record AuditEvent(
        String action,
        String entityType,
        String entityId,
        Long actorId,
        String actorUsername,
        String actorRole,
        String beforeJson,
        String afterJson,
        String reason,
        String ipAddress) {

    public static AuditEvent of(String action, String entityType, String entityId) {
        return new AuditEvent(action, entityType, entityId, null, null, null, null, null, null, null);
    }

    public AuditEvent withActor(Long id, String username, String role) {
        return new AuditEvent(action, entityType, entityId, id, username, role, beforeJson, afterJson, reason, ipAddress);
    }

    public AuditEvent withActor(AuthenticatedUser actor) {
        return actor == null ? this : withActor(actor.id(), actor.username(), actor.role().name());
    }

    public AuditEvent withChange(String before, String after) {
        return new AuditEvent(action, entityType, entityId, actorId, actorUsername, actorRole, before, after, reason, ipAddress);
    }

    public AuditEvent withReason(String value) {
        return new AuditEvent(action, entityType, entityId, actorId, actorUsername, actorRole, beforeJson, afterJson, value, ipAddress);
    }

    public AuditEvent withIp(String value) {
        return new AuditEvent(action, entityType, entityId, actorId, actorUsername, actorRole, beforeJson, afterJson, reason, value);
    }
}
