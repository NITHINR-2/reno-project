package com.renault.fleet.audit.controller;

import com.renault.fleet.audit.dto.AuditLogDto;
import com.renault.fleet.audit.dto.AuditLogFilter;
import com.renault.fleet.audit.service.AuditLogQueryService;
import com.renault.fleet.common.api.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Admin: audit log")
public class AuditLogController {

    private final AuditLogQueryService service;

    public AuditLogController(AuditLogQueryService service) {
        this.service = service;
    }

    @GetMapping("/api/admin/audit-logs")
    @PreAuthorize("hasAuthority('AUDIT_LOG_READ')")
    @Operation(summary = "Search the audit log (actor, action, entity, date range); newest first")
    public PageResponse<AuditLogDto> search(@ParameterObject AuditLogFilter filter,
            @ParameterObject @PageableDefault(size = 50, sort = {"createdAt", "id"}, direction = Sort.Direction.DESC) Pageable pageable) {
        return service.search(filter, pageable);
    }
}
