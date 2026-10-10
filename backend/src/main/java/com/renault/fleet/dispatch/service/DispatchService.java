package com.renault.fleet.dispatch.service;

import com.renault.fleet.audit.service.AuditEvent;
import com.renault.fleet.audit.service.AuditService;
import com.renault.fleet.common.error.BadRequestException;
import com.renault.fleet.common.error.ConflictException;
import com.renault.fleet.common.error.NotFoundException;
import com.renault.fleet.dispatch.dto.ScheduleDispatchRequest;
import com.renault.fleet.dispatch.entity.Dispatch;
import com.renault.fleet.dispatch.repository.DispatchRepository;
import com.renault.fleet.fleetrequest.dto.FleetRequestDetailDto;
import com.renault.fleet.fleetrequest.dto.TransitionRequest;
import com.renault.fleet.fleetrequest.entity.FleetRequest;
import com.renault.fleet.fleetrequest.repository.FleetRequestRepository;
import com.renault.fleet.inventory.entity.InventoryLocation;
import com.renault.fleet.inventory.repository.InventoryLocationRepository;
import com.renault.fleet.security.AuthenticatedUser;
import com.renault.fleet.user.repository.UserRepository;
import com.renault.fleet.workflow.entity.WorkflowAction;
import com.renault.fleet.workflow.service.WorkflowService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Schedules dispatch (from FINANCIALLY_CLEARED when nothing is produced, otherwise from PRODUCTION_SCHEDULED) and
 * completes delivery. Completion side effects (stock consumed, orders closed) run in the WorkflowService.
 */
@Service
public class DispatchService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final FleetRequestRepository requests;
    private final InventoryLocationRepository locations;
    private final DispatchRepository dispatches;
    private final UserRepository users;
    private final WorkflowService workflow;
    private final AuditService audit;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public DispatchService(FleetRequestRepository requests, InventoryLocationRepository locations,
            DispatchRepository dispatches, UserRepository users, WorkflowService workflow, AuditService audit,
            JsonMapper jsonMapper, Clock clock) {
        this.requests = requests;
        this.locations = locations;
        this.dispatches = dispatches;
        this.users = users;
        this.workflow = workflow;
        this.audit = audit;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    @Transactional
    public FleetRequestDetailDto schedule(Long requestId, ScheduleDispatchRequest body, AuthenticatedUser actor) {
        LocalDate today = LocalDate.now(clock.withZone(IST));
        if (body.scheduledDate().isBefore(today)) {
            throw new BadRequestException("scheduledDate cannot be in the past (today is " + today + " IST).");
        }
        FleetRequestDetailDto result = workflow.transition(requestId, WorkflowAction.SCHEDULE_DISPATCH,
                new TransitionRequest(null, body.version()), actor);
        FleetRequest request = requests.findByIdForUpdate(requestId).orElseThrow();

        Dispatch dispatch = new Dispatch("DSP-%d-%05d".formatted(today.getYear(), dispatches.nextDispatchNumber()),
                request, request.getQuantity(), body.scheduledDate(), users.getReferenceById(actor.id()));
        if (body.fromLocationId() != null) {
            InventoryLocation from = locations.findById(body.fromLocationId())
                    .orElseThrow(() -> new NotFoundException("Location " + body.fromLocationId() + " not found."));
            if (!from.isActive()) {
                throw new ConflictException("Location " + from.getCode() + " is not active.");
            }
            dispatch.setFromLocation(from);
        }
        dispatch.setCarrier(body.carrier());
        dispatch.setVehicleReference(body.vehicleReference());
        dispatch.setNotes(body.notes());
        dispatches.saveAndFlush(dispatch);

        Map<String, Object> after = new HashMap<>();
        after.put("dispatchNumber", dispatch.getDispatchNumber());
        after.put("fleetRequestId", requestId);
        after.put("quantity", dispatch.getQuantity());
        after.put("scheduledDate", body.scheduledDate().toString());
        after.put("fromLocationId", body.fromLocationId());
        audit.record(AuditEvent.of("CREATE", "DISPATCH", String.valueOf(dispatch.getId()))
                .withActor(actor).withChange(null, jsonMapper.writeValueAsString(after)));
        return result;
    }

    @Transactional
    public FleetRequestDetailDto complete(Long requestId, TransitionRequest body, AuthenticatedUser actor) {
        return workflow.transition(requestId, WorkflowAction.COMPLETE, body, actor);
    }
}
