package com.renault.fleet.audit.dto;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/** Query-string filters for {@code GET /api/admin/audit-logs}. Dates are IST calendar days, both inclusive. */
public record AuditLogFilter(
        Long actorId,
        String actorUsername,
        String action,
        String entityType,
        String entityId,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
}
