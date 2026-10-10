package com.renault.fleet.supervisor.service;

import com.renault.fleet.audit.service.AuditEvent;
import com.renault.fleet.audit.service.AuditService;
import com.renault.fleet.common.error.BadRequestException;
import com.renault.fleet.common.error.ConflictException;
import com.renault.fleet.common.error.NotFoundException;
import com.renault.fleet.common.persistence.Versions;
import com.renault.fleet.fleetrequest.dto.FleetRequestDetailDto;
import com.renault.fleet.fleetrequest.entity.FleetRequest;
import com.renault.fleet.fleetrequest.mapper.FleetRequestAuditMapper;
import com.renault.fleet.fleetrequest.repository.FleetRequestRepository;
import com.renault.fleet.fleetrequest.service.FleetRequestDetailAssembler;
import com.renault.fleet.production.entity.Plant;
import com.renault.fleet.production.repository.PlantRepository;
import com.renault.fleet.security.AuthenticatedUser;
import com.renault.fleet.supervisor.dto.OverrideRequest;
import com.renault.fleet.supervisor.dto.ReassignRequest;
import com.renault.fleet.user.entity.RoleCode;
import com.renault.fleet.user.entity.User;
import com.renault.fleet.user.repository.UserRepository;
import com.renault.fleet.workflow.service.WorkflowHistoryService;
import com.renault.fleet.workflow.service.WorkflowService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Audited Supervisor powers. Overrides touch only allow-listed fields and never status, quantities, stock, money
 * calculations or receipts; every override needs a reason and writes before/after audit plus an is_override
 * history row. Cancellation goes through the WorkflowService (it releases stock).
 */
@Service
public class SupervisorService {

    /** The only fields an override may change. */
    public static final Set<String> OVERRIDABLE = Set.of(
            "requiredDeposit", "productionEstimateWeeks", "requestedDeliveryDate", "plantId", "notes");

    private final FleetRequestRepository requests;
    private final PlantRepository plants;
    private final UserRepository users;
    private final WorkflowService workflow;
    private final WorkflowHistoryService history;
    private final AuditService audit;
    private final FleetRequestAuditMapper auditMapper;
    private final FleetRequestDetailAssembler assembler;

    public SupervisorService(FleetRequestRepository requests, PlantRepository plants, UserRepository users,
            WorkflowService workflow, WorkflowHistoryService history, AuditService audit,
            FleetRequestAuditMapper auditMapper, FleetRequestDetailAssembler assembler) {
        this.requests = requests;
        this.plants = plants;
        this.users = users;
        this.workflow = workflow;
        this.history = history;
        this.audit = audit;
        this.auditMapper = auditMapper;
        this.assembler = assembler;
    }

    @Transactional
    public FleetRequestDetailDto override(Long requestId, OverrideRequest body, AuthenticatedUser actor) {
        Set<String> rejected = new TreeSet<>(body.changes().keySet());
        rejected.removeAll(OVERRIDABLE);
        if (!rejected.isEmpty()) {
            throw new BadRequestException("Field(s) " + rejected + " cannot be overridden. Allowed: " + new TreeSet<>(OVERRIDABLE) + ".");
        }
        FleetRequest request = lockOpen(requestId, body.version());
        String before = auditMapper.json(request);

        Map<String, String> c = body.changes();
        if (c.containsKey("requiredDeposit")) request.setRequiredDeposit(money(c.get("requiredDeposit")));
        if (c.containsKey("productionEstimateWeeks")) request.setProductionEstimateWeeks(weeks(c.get("productionEstimateWeeks")));
        if (c.containsKey("requestedDeliveryDate")) request.setRequestedDeliveryDate(date(c.get("requestedDeliveryDate")));
        if (c.containsKey("plantId")) request.setPlant(plant(c.get("plantId")));
        if (c.containsKey("notes")) request.setNotes(notes(c.get("notes")));
        requests.flush();

        String reason = body.reason().strip();
        history.recordOverride(request, "OVERRIDE", actor, reason,
                auditMapper.toJson(Map.of("fields", List.copyOf(new TreeSet<>(c.keySet())))));
        audit.record(AuditEvent.of("OVERRIDE", "FLEET_REQUEST", String.valueOf(requestId))
                .withActor(actor).withChange(before, auditMapper.json(request)).withReason(reason));
        // A lowered deposit requirement may already be covered by approved deposits.
        workflow.verifyFundsIfCovered(requestId, actor);
        return assembler.toDetail(request, actor.role());
    }

    @Transactional
    public FleetRequestDetailDto reassign(Long requestId, ReassignRequest body, AuthenticatedUser actor) {
        FleetRequest request = lockOpen(requestId, body.version());
        User assignee = users.findById(body.assigneeId())
                .orElseThrow(() -> new NotFoundException("User " + body.assigneeId() + " not found."));
        if (!assignee.isActive() || assignee.getRole().getCode() == RoleCode.ADMIN) {
            throw new ConflictException("Requests can only be assigned to active users with fleet access.");
        }
        String before = auditMapper.json(request);
        Long previous = request.getAssignedTo() == null ? null : request.getAssignedTo().getId();
        request.setAssignedTo(assignee);
        requests.flush();

        String reason = body.reason().strip();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("fromUserId", previous);
        metadata.put("toUserId", assignee.getId());
        history.record(request, request.getStatus(), request.getStatus(), "REASSIGNED", actor, reason,
                auditMapper.toJson(metadata));
        audit.record(AuditEvent.of("REASSIGN", "FLEET_REQUEST", String.valueOf(requestId))
                .withActor(actor).withChange(before, auditMapper.json(request)).withReason(reason));
        return assembler.toDetail(request, actor.role());
    }

    private FleetRequest lockOpen(Long requestId, Long version) {
        FleetRequest request = requests.findByIdForUpdate(requestId)
                .orElseThrow(() -> new NotFoundException("Fleet request " + requestId + " not found."));
        Versions.check(request, version);
        if (request.getStatus().isTerminal()) {
            throw new ConflictException("Request is " + request.getStatus() + "; closed requests cannot be changed.");
        }
        return request;
    }

    private static BigDecimal money(String value) {
        if (value == null) {
            throw new BadRequestException("requiredDeposit cannot be null.");
        }
        try {
            BigDecimal amount = new BigDecimal(value.strip());
            if (amount.signum() < 0 || amount.scale() > 2 || amount.precision() - amount.scale() > 13) {
                throw new BadRequestException("requiredDeposit must be ≥ 0 with at most 2 decimals and 13 integer digits.");
            }
            return amount.setScale(2);
        } catch (NumberFormatException ex) {
            throw new BadRequestException("requiredDeposit must be a number.");
        }
    }

    private static Integer weeks(String value) {
        if (value == null) return null;
        try {
            int weeks = Integer.parseInt(value.strip());
            if (weeks < 0 || weeks > 104) throw new BadRequestException("productionEstimateWeeks must be 0–104.");
            return weeks;
        } catch (NumberFormatException ex) {
            throw new BadRequestException("productionEstimateWeeks must be a whole number.");
        }
    }

    private static LocalDate date(String value) {
        if (value == null) return null;
        try {
            return LocalDate.parse(value.strip());
        } catch (DateTimeParseException ex) {
            throw new BadRequestException("requestedDeliveryDate must be an ISO date (yyyy-MM-dd).");
        }
    }

    private Plant plant(String value) {
        if (value == null) return null;
        long id;
        try {
            id = Long.parseLong(value.strip());
        } catch (NumberFormatException ex) {
            throw new BadRequestException("plantId must be a number.");
        }
        Plant plant = plants.findById(id).orElseThrow(() -> new NotFoundException("Plant " + id + " not found."));
        if (!plant.isActive()) throw new ConflictException("Plant " + plant.getCode() + " is not active.");
        return plant;
    }

    private static String notes(String value) {
        if (value != null && value.length() > 4000) throw new BadRequestException("notes must be at most 4000 characters.");
        return value;
    }
}
