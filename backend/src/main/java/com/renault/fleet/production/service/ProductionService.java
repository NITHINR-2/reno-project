package com.renault.fleet.production.service;

import com.renault.fleet.audit.service.AuditEvent;
import com.renault.fleet.audit.service.AuditService;
import com.renault.fleet.common.error.BadRequestException;
import com.renault.fleet.common.error.ConflictException;
import com.renault.fleet.common.error.NotFoundException;
import com.renault.fleet.fleetrequest.dto.FleetRequestDetailDto;
import com.renault.fleet.fleetrequest.dto.TransitionRequest;
import com.renault.fleet.fleetrequest.entity.FleetRequest;
import com.renault.fleet.fleetrequest.repository.FleetRequestRepository;
import com.renault.fleet.production.dto.ScheduleProductionRequest;
import com.renault.fleet.production.entity.Plant;
import com.renault.fleet.production.entity.ProductionOrder;
import com.renault.fleet.production.repository.PlantRepository;
import com.renault.fleet.production.repository.ProductionOrderRepository;
import com.renault.fleet.security.AuthenticatedUser;
import com.renault.fleet.user.repository.UserRepository;
import com.renault.fleet.workflow.entity.WorkflowAction;
import com.renault.fleet.workflow.service.WorkflowService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * FINANCIALLY_CLEARED → PRODUCTION_SCHEDULED with a production order for exactly {@code productionRequired} units.
 * When nothing needs producing the workflow guard refuses (409) and dispatch is scheduled directly instead.
 */
@Service
public class ProductionService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final FleetRequestRepository requests;
    private final PlantRepository plants;
    private final ProductionOrderRepository orders;
    private final UserRepository users;
    private final WorkflowService workflow;
    private final AuditService audit;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public ProductionService(FleetRequestRepository requests, PlantRepository plants, ProductionOrderRepository orders,
            UserRepository users, WorkflowService workflow, AuditService audit, JsonMapper jsonMapper, Clock clock) {
        this.requests = requests;
        this.plants = plants;
        this.orders = orders;
        this.users = users;
        this.workflow = workflow;
        this.audit = audit;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    @Transactional
    public FleetRequestDetailDto schedule(Long requestId, ScheduleProductionRequest body, AuthenticatedUser actor) {
        LocalDate today = LocalDate.now(clock.withZone(IST));
        if (body.scheduledStart().isBefore(today)) {
            throw new BadRequestException("scheduledStart cannot be in the past (today is " + today + " IST).");
        }
        if (body.estimatedCompletion().isBefore(body.scheduledStart())) {
            throw new BadRequestException("estimatedCompletion must be on or after scheduledStart.");
        }
        // Role, state and the productionRequired > 0 guard are checked first; any later failure rolls this back.
        FleetRequestDetailDto result = workflow.transition(requestId, WorkflowAction.SCHEDULE_PRODUCTION,
                new TransitionRequest(null, body.version()), actor);
        FleetRequest request = requests.findByIdForUpdate(requestId).orElseThrow();

        Plant plant = body.plantId() != null
                ? plants.findById(body.plantId()).orElseThrow(() -> new NotFoundException("Plant " + body.plantId() + " not found."))
                : request.getPlant();
        if (plant == null) {
            throw new BadRequestException("The request has no plant; pass plantId.");
        }
        if (!plant.isActive()) {
            throw new ConflictException("Plant " + plant.getCode() + " is not active.");
        }
        ProductionOrder order = new ProductionOrder("PRD-%d-%05d".formatted(today.getYear(), orders.nextProductionNumber()),
                request, plant, request.getVariant(), request.getProductionRequired(), body.scheduledStart(),
                body.estimatedCompletion(), users.getReferenceById(actor.id()));
        order.setNotes(body.notes());
        orders.saveAndFlush(order);
        audit.record(AuditEvent.of("CREATE", "PRODUCTION_ORDER", String.valueOf(order.getId()))
                .withActor(actor).withChange(null, jsonMapper.writeValueAsString(Map.of(
                        "productionNumber", order.getProductionNumber(), "fleetRequestId", requestId,
                        "plantId", plant.getId(), "quantity", order.getQuantity(),
                        "scheduledStart", body.scheduledStart().toString(),
                        "estimatedCompletion", body.estimatedCompletion().toString()))));
        return result;
    }
}
