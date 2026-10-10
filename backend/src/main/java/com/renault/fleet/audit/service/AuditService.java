package com.renault.fleet.audit.service;

import com.renault.fleet.audit.entity.AuditLog;
import com.renault.fleet.audit.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes audit rows. Joins the caller's transaction (so an audited change and its audit row commit or roll back
 * together) or opens its own when called outside one.
 */
@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public void record(AuditEvent event) {
        auditLogRepository.save(new AuditLog(
                event.actorId(),
                truncate(event.actorUsername(), 60),
                event.actorRole(),
                event.action(),
                event.entityType(),
                truncate(event.entityId(), 60),
                event.beforeJson(),
                event.afterJson(),
                truncate(event.reason(), 1000),
                truncate(event.ipAddress(), 45)));
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
