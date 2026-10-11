package com.renault.fleet.sla.service;

import com.renault.fleet.audit.service.AuditEvent;
import com.renault.fleet.audit.service.AuditService;
import com.renault.fleet.common.api.PageResponse;
import com.renault.fleet.common.error.NotFoundException;
import com.renault.fleet.common.persistence.Versions;
import com.renault.fleet.security.AuthenticatedUser;
import com.renault.fleet.sla.dto.SlaPolicyDto;
import com.renault.fleet.sla.dto.UpdateSlaPolicyRequest;
import com.renault.fleet.sla.entity.SlaPolicy;
import com.renault.fleet.sla.repository.SlaPolicyRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** Policy edits apply to stages entered afterwards; running clocks keep the deadlines they started with. */
@Service
public class SlaPolicyService {

    private final SlaPolicyRepository policies;
    private final AuditService audit;
    private final JsonMapper jsonMapper;

    public SlaPolicyService(SlaPolicyRepository policies, AuditService audit, JsonMapper jsonMapper) {
        this.policies = policies;
        this.audit = audit;
        this.jsonMapper = jsonMapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<SlaPolicyDto> list(Pageable pageable) {
        return PageResponse.from(policies.findAll(pageable), SlaPolicyService::toDto);
    }

    @Transactional
    public SlaPolicyDto update(Long id, UpdateSlaPolicyRequest body, AuthenticatedUser actor) {
        SlaPolicy policy = policies.findById(id).orElseThrow(() -> new NotFoundException("SLA policy " + id + " not found."));
        Versions.check(policy, body.version());
        String before = jsonMapper.writeValueAsString(toDto(policy));
        if (body.hoursAllowed() != null) {
            policy.setHoursAllowed(body.hoursAllowed());
        }
        if (body.warningThresholdPct() != null) {
            policy.setWarningThresholdPct(body.warningThresholdPct());
        }
        if (body.active() != null) {
            policy.setActive(body.active());
        }
        policies.flush();
        SlaPolicyDto saved = toDto(policy);
        audit.record(AuditEvent.of("SLA_POLICY_UPDATE", "SLA_POLICY", policy.getStage().name())
                .withActor(actor)
                .withChange(before, jsonMapper.writeValueAsString(saved)));
        return saved;
    }

    static SlaPolicyDto toDto(SlaPolicy p) {
        return new SlaPolicyDto(p.getId(), p.getStage(), p.getHoursAllowed(), p.getWarningThresholdPct(), p.isActive(),
                p.getVersion(), p.getUpdatedAt());
    }
}
