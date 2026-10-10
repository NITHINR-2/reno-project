package com.renault.fleet.workflow.service;

import com.renault.fleet.audit.service.AuditEvent;
import com.renault.fleet.audit.service.AuditService;
import com.renault.fleet.common.error.BadRequestException;
import com.renault.fleet.common.error.ConflictException;
import com.renault.fleet.common.error.NotFoundException;
import com.renault.fleet.common.persistence.Versions;
import com.renault.fleet.fleetrequest.dto.FleetRequestDetailDto;
import com.renault.fleet.fleetrequest.dto.TransitionRequest;
import com.renault.fleet.fleetrequest.entity.FleetRequest;
import com.renault.fleet.fleetrequest.entity.FleetRequestStatus;
import com.renault.fleet.fleetrequest.mapper.FleetRequestAuditMapper;
import com.renault.fleet.fleetrequest.repository.FleetRequestRepository;
import com.renault.fleet.fleetrequest.service.FleetRequestDetailAssembler;
import com.renault.fleet.security.AuthenticatedUser;
import com.renault.fleet.workflow.entity.WorkflowAction;
import com.renault.fleet.workflow.service.WorkflowTransitions.Resolution;
import com.renault.fleet.workflow.service.WorkflowTransitions.Transition;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only way a fleet request changes status. Each transition is checked against {@link WorkflowTransitions}
 * (403 for a role that may never take the action, 409 for a wrong state or failed guard), and writes one
 * {@code workflow_history} row and one {@code audit_logs} row in the same transaction.
 */
@Service
public class WorkflowService {

    private final FleetRequestRepository requests;
    private final WorkflowHistoryService history;
    private final AuditService audit;
    private final FleetRequestAuditMapper auditMapper;
    private final TransitionEffects effects;
    private final FleetRequestDetailAssembler assembler;
    private final WorkflowFacts facts;

    public WorkflowService(FleetRequestRepository requests, WorkflowHistoryService history, AuditService audit,
            FleetRequestAuditMapper auditMapper, TransitionEffects effects,
            FleetRequestDetailAssembler assembler, WorkflowFacts facts) {
        this.requests = requests;
        this.history = history;
        this.audit = audit;
        this.auditMapper = auditMapper;
        this.effects = effects;
        this.assembler = assembler;
        this.facts = facts;
    }

    @Transactional
    public FleetRequestDetailDto transition(Long requestId, WorkflowAction action, TransitionRequest command,
            AuthenticatedUser actor) {
        TransitionRequest cmd = command == null ? TransitionRequest.EMPTY : command;
        FleetRequest request = requests.findByIdForUpdate(requestId)
                .orElseThrow(() -> new NotFoundException("Fleet request " + requestId + " not found."));

        Transition transition = switch (WorkflowTransitions.resolve(request.getStatus(), action, actor.role())) {
            case Resolution.Forbidden f -> throw new AccessDeniedException(actor.role() + " may not " + action);
            case Resolution.Invalid i -> throw new ConflictException(
                    "Cannot " + action + " a request in status " + request.getStatus() + ".");
            case Resolution.Allowed a -> a.transition();
        };
        Versions.check(request, cmd.version());
        String reason = cmd.reason() == null || cmd.reason().isBlank() ? null : cmd.reason().strip();
        if (transition.reasonRequired() && reason == null) {
            throw new BadRequestException(action + " requires a reason.");
        }
        Optional<String> guardFailure = transition.guard().check(request, facts);
        if (guardFailure.isPresent()) {
            throw new ConflictException(guardFailure.get());
        }

        apply(request, transition, reason, actor);
        WorkflowTransitions.automaticFrom(request.getStatus())
                .ifPresent(auto -> apply(request, auto, null, null));
        return assembler.toDetail(request, actor.role());
    }

    /**
     * PAYMENT_PENDING → FUNDS_VERIFIED when approved deposits now cover the required deposit (called after a receipt
     * approval or a quotation issue). Returns whether the transition happened; does nothing in any other state.
     */
    @Transactional
    public boolean verifyFundsIfCovered(Long requestId, AuthenticatedUser actor) {
        FleetRequest request = requests.findByIdForUpdate(requestId)
                .orElseThrow(() -> new NotFoundException("Fleet request " + requestId + " not found."));
        requests.flush();
        if (request.getStatus() != FleetRequestStatus.PAYMENT_PENDING
                || facts.eligibleDeposits(requestId).compareTo(request.getRequiredDeposit()) < 0) {
            return false;
        }
        transition(requestId, WorkflowAction.VERIFY_FUNDS, null, actor);
        return true;
    }

    private void apply(FleetRequest request, Transition transition, String reason, AuthenticatedUser actor) {
        String before = auditMapper.json(request);
        FleetRequestStatus from = request.getStatus();
        Map<String, Object> effects = this.effects.apply(request, transition.action(), reason);
        String metadata = effects.isEmpty() ? null : auditMapper.toJson(effects);
        request.setStatus(transition.to());
        requests.flush();

        history.record(request, from, transition.to(), transition.action().name(), actor, reason, metadata);
        audit.record(AuditEvent.of("WORKFLOW_" + transition.action().name(), "FLEET_REQUEST",
                        String.valueOf(request.getId()))
                .withActor(actor)
                .withChange(before, auditMapper.json(request))
                .withReason(reason));
    }
}
