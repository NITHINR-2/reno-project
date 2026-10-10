package com.renault.fleet.audit.service;

import com.renault.fleet.audit.dto.AuditLogDto;
import com.renault.fleet.audit.dto.AuditLogFilter;
import com.renault.fleet.audit.entity.AuditLog;
import com.renault.fleet.audit.repository.AuditLogRepository;
import com.renault.fleet.common.api.PageResponse;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** Read-only search over the append-only audit trail. */
@Service
@Transactional(readOnly = true)
public class AuditLogQueryService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final AuditLogRepository repository;
    private final JsonMapper jsonMapper;

    public AuditLogQueryService(AuditLogRepository repository, JsonMapper jsonMapper) {
        this.repository = repository;
        this.jsonMapper = jsonMapper;
    }

    public PageResponse<AuditLogDto> search(AuditLogFilter f, Pageable pageable) {
        List<Specification<AuditLog>> specs = new ArrayList<>();
        if (f.actorId() != null) {
            specs.add((root, q, cb) -> cb.equal(root.get("actorId"), f.actorId()));
        }
        if (notBlank(f.actorUsername())) {
            specs.add((root, q, cb) -> cb.equal(cb.lower(root.get("actorUsername")), f.actorUsername().strip().toLowerCase(Locale.ROOT)));
        }
        if (notBlank(f.action())) {
            specs.add((root, q, cb) -> cb.equal(root.get("action"), f.action().strip().toUpperCase(Locale.ROOT)));
        }
        if (notBlank(f.entityType())) {
            specs.add((root, q, cb) -> cb.equal(root.get("entityType"), f.entityType().strip().toUpperCase(Locale.ROOT)));
        }
        if (notBlank(f.entityId())) {
            specs.add((root, q, cb) -> cb.equal(root.get("entityId"), f.entityId().strip()));
        }
        if (f.from() != null) {
            specs.add((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), f.from().atStartOfDay(IST).toInstant()));
        }
        if (f.to() != null) {
            specs.add((root, q, cb) -> cb.lessThan(root.get("createdAt"), f.to().plusDays(1).atStartOfDay(IST).toInstant()));
        }
        return PageResponse.from(repository.findAll(Specification.allOf(specs), pageable), this::toDto);
    }

    private AuditLogDto toDto(AuditLog a) {
        return new AuditLogDto(a.getId(), a.getActorId(), a.getActorUsername(), a.getActorRole(), a.getAction(),
                a.getEntityType(), a.getEntityId(), a.getBeforeJson() == null ? null : jsonMapper.readTree(a.getBeforeJson()),
                a.getAfterJson() == null ? null : jsonMapper.readTree(a.getAfterJson()), a.getReason(), a.getIpAddress(),
                a.getCreatedAt());
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
