package com.renault.fleet.workflow.service;

import com.renault.fleet.common.api.PageResponse;
import com.renault.fleet.fleetrequest.entity.FleetRequest;
import com.renault.fleet.fleetrequest.entity.FleetRequestStatus;
import com.renault.fleet.security.AuthenticatedUser;
import com.renault.fleet.user.entity.User;
import com.renault.fleet.user.repository.UserRepository;
import com.renault.fleet.workflow.dto.WorkflowHistoryDto;
import com.renault.fleet.workflow.entity.WorkflowHistory;
import com.renault.fleet.workflow.repository.WorkflowHistoryRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** Appends to and reads the per-request workflow timeline. Writes join the caller's transaction. */
@Service
public class WorkflowHistoryService {

    private final WorkflowHistoryRepository repository;
    private final UserRepository userRepository;
    private final JsonMapper jsonMapper;

    public WorkflowHistoryService(WorkflowHistoryRepository repository, UserRepository userRepository, JsonMapper jsonMapper) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.jsonMapper = jsonMapper;
    }

    /** Records an event by a user; {@code actor == null} records a system event. */
    @Transactional
    public void record(FleetRequest request, FleetRequestStatus from, FleetRequestStatus to, String action,
            AuthenticatedUser actor, String reason, String metadataJson) {
        save(request, from, to, action, actor, reason, false, metadataJson);
    }

    /** Records a Supervisor override (is_override = true; the database requires a reason). */
    @Transactional
    public void recordOverride(FleetRequest request, String action, AuthenticatedUser actor, String reason,
            String metadataJson) {
        save(request, request.getStatus(), request.getStatus(), action, actor, reason, true, metadataJson);
    }

    private void save(FleetRequest request, FleetRequestStatus from, FleetRequestStatus to, String action,
            AuthenticatedUser actor, String reason, boolean override, String metadataJson) {
        User actorRef = actor == null ? null : userRepository.getReferenceById(actor.id());
        String role = actor == null ? null : actor.role().name();
        repository.save(new WorkflowHistory(request, from, to, action, actorRef, role, reason, override, metadataJson));
    }

    @Transactional(readOnly = true)
    public PageResponse<WorkflowHistoryDto> page(Long requestId, Pageable pageable) {
        return PageResponse.from(repository.findByFleetRequestId(requestId, pageable), this::toDto);
    }

    private WorkflowHistoryDto toDto(WorkflowHistory h) {
        User actor = h.getActor();
        return new WorkflowHistoryDto(h.getId(), h.getFromStatus(), h.getToStatus(), h.getAction(),
                actor == null ? null : actor.getId(), actor == null ? null : actor.getFullName(), h.getActorRole(),
                h.getReason(), h.isOverride(), h.getMetadata() == null ? null : jsonMapper.readTree(h.getMetadata()),
                h.getCreatedAt());
    }
}
