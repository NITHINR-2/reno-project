package com.renault.fleet.fleetrequest.service;

import com.renault.fleet.audit.service.AuditEvent;
import com.renault.fleet.audit.service.AuditService;
import com.renault.fleet.catalog.entity.VehicleVariant;
import com.renault.fleet.catalog.repository.VehicleVariantRepository;
import com.renault.fleet.common.api.PageResponse;
import com.renault.fleet.common.error.ConflictException;
import com.renault.fleet.common.error.NotFoundException;
import com.renault.fleet.common.persistence.Versions;
import com.renault.fleet.dealer.entity.Dealer;
import com.renault.fleet.dealer.entity.DealerStatus;
import com.renault.fleet.dealer.repository.DealerRepository;
import com.renault.fleet.dealerportal.entity.DealerOrder;
import com.renault.fleet.fleetrequest.dto.CreateFleetRequestDto;
import com.renault.fleet.fleetrequest.dto.FleetRequestDetailDto;
import com.renault.fleet.fleetrequest.dto.FleetRequestFilter;
import com.renault.fleet.fleetrequest.dto.FleetRequestSummaryDto;
import com.renault.fleet.fleetrequest.dto.UpdateFleetRequestDto;
import com.renault.fleet.fleetrequest.entity.FleetRequest;
import com.renault.fleet.fleetrequest.entity.FleetRequestStatus;
import com.renault.fleet.fleetrequest.mapper.FleetRequestAuditMapper;
import com.renault.fleet.fleetrequest.mapper.FleetRequestMapper;
import com.renault.fleet.fleetrequest.repository.FleetRequestRepository;
import com.renault.fleet.fleetrequest.repository.FleetRequestSpecifications;
import com.renault.fleet.inventory.service.StockAllocationService;
import com.renault.fleet.production.entity.Plant;
import com.renault.fleet.production.repository.PlantRepository;
import com.renault.fleet.security.AuthenticatedUser;
import com.renault.fleet.sla.service.SlaService;
import com.renault.fleet.user.repository.UserRepository;
import com.renault.fleet.workflow.dto.WorkflowHistoryDto;
import com.renault.fleet.workflow.service.WorkflowHistoryService;
import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Create, edit and query fleet requests. Status changes go through {@code WorkflowService}, never here. */
@Service
public class FleetRequestService {

    public static final String CREATED = "CREATED";
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final FleetRequestRepository repository;
    private final DealerRepository dealers;
    private final VehicleVariantRepository variants;
    private final PlantRepository plants;
    private final UserRepository users;
    private final StockAllocationService stockAllocations;
    private final WorkflowHistoryService history;
    private final AuditService audit;
    private final FleetRequestMapper mapper;
    private final FleetRequestAuditMapper auditMapper;
    private final FleetRequestDetailAssembler assembler;
    private final Clock clock;
    private final SlaService sla;

    public FleetRequestService(FleetRequestRepository repository, DealerRepository dealers,
            VehicleVariantRepository variants, PlantRepository plants, UserRepository users,
            StockAllocationService stockAllocations, WorkflowHistoryService history, AuditService audit,
            FleetRequestMapper mapper, FleetRequestAuditMapper auditMapper, FleetRequestDetailAssembler assembler,
            Clock clock, SlaService sla) {
        this.repository = repository;
        this.dealers = dealers;
        this.variants = variants;
        this.plants = plants;
        this.users = users;
        this.stockAllocations = stockAllocations;
        this.history = history;
        this.audit = audit;
        this.mapper = mapper;
        this.auditMapper = auditMapper;
        this.assembler = assembler;
        this.clock = clock;
        this.sla = sla;
    }

    @Transactional(readOnly = true)
    public PageResponse<FleetRequestSummaryDto> list(FleetRequestFilter filter, Pageable pageable) {
        return PageResponse.from(repository.findAll(FleetRequestSpecifications.from(filter, clock.instant()), pageable), mapper::toSummary);
    }

    @Transactional(readOnly = true)
    public FleetRequestDetailDto get(Long id, AuthenticatedUser viewer) {
        return assembler.byId(id, viewer.role());
    }

    @Transactional(readOnly = true)
    public PageResponse<WorkflowHistoryDto> history(Long id, Pageable pageable) {
        if (!repository.existsById(id)) {
            throw new NotFoundException("Fleet request " + id + " not found.");
        }
        return history.page(id, pageable);
    }

    @Transactional
    public FleetRequestDetailDto create(CreateFleetRequestDto dto, AuthenticatedUser actor) {
        Dealer dealer = dealers.findById(dto.dealerId())
                .orElseThrow(() -> new NotFoundException("Dealer " + dto.dealerId() + " not found."));
        if (dealer.getStatus() != DealerStatus.ACTIVE) {
            throw new ConflictException("Dealer " + dealer.getDealerCode() + " is " + dealer.getStatus()
                    + "; only ACTIVE dealers can raise fleet requests.");
        }
        FleetRequest request = new FleetRequest(nextRequestNumber(), dealer, activeVariant(dto.variantId()),
                dto.quantity(), users.getReferenceById(actor.id()));
        request.setPlant(plant(dto.plantId()));
        request.setRequiredDeposit(dto.requiredDeposit());
        request.setProductionEstimateWeeks(dto.productionEstimateWeeks());
        request.setRequestedDeliveryDate(dto.requestedDeliveryDate());
        request.setNotes(dto.notes());
        persistNew(request, actor);
        return assembler.toDetail(request, actor.role());
    }

    /**
     * One line of a Dealer Portal order. Enters PENDING_MARKETING_REVIEW with no plant and a zero required deposit;
     * Marketing sets both during review. The caller has checked that the dealer is ACTIVE and owns the order.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public FleetRequest createForDealerOrder(DealerOrder order, int lineNo, VehicleVariant variant, String colour,
            int quantity, AuthenticatedUser actor) {
        FleetRequest request = new FleetRequest(nextRequestNumber(), order.getDealer(), variant, quantity,
                users.getReferenceById(actor.id()));
        request.linkToDealerOrder(order, lineNo);
        request.setColour(colour);
        request.setRequestedDeliveryDate(order.getRequestedDeliveryDate());
        request.setNotes(order.getNotes());
        persistNew(request, actor);
        return request;
    }

    private void persistNew(FleetRequest request, AuthenticatedUser actor) {
        sla.enterStage(request);
        repository.saveAndFlush(request);
        history.record(request, null, request.getStatus(), CREATED, actor, null, null);
        audit.record(AuditEvent.of("CREATE", "FLEET_REQUEST", String.valueOf(request.getId()))
                .withActor(actor).withChange(null, auditMapper.json(request)));
    }

    @Transactional
    public FleetRequestDetailDto update(Long id, UpdateFleetRequestDto dto, AuthenticatedUser actor) {
        FleetRequest request = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Fleet request " + id + " not found."));
        Versions.check(request, dto.version());
        if (request.getStatus() != FleetRequestStatus.PENDING_MARKETING_REVIEW) {
            throw new ConflictException("Requests can only be edited in PENDING_MARKETING_REVIEW (it is "
                    + request.getStatus() + ").");
        }
        String before = auditMapper.json(request);

        if (dto.variantId() != null && !dto.variantId().equals(request.getVariant().getId())) {
            // Allocated stock belongs to the old variant: give it back and require a fresh mapping.
            stockAllocations.releaseAll(request);
            request.clearStockMapping();
            request.setVariant(activeVariant(dto.variantId()));
        }
        if (dto.quantity() != null) {
            if (dto.quantity() < request.getAllocatedQty()) {
                throw new ConflictException("Quantity " + dto.quantity() + " is below the " + request.getAllocatedQty()
                        + " units already allocated; re-map stock first.");
            }
            request.setQuantity(dto.quantity());
        }
        if (dto.plantId() != null) {
            request.setPlant(plant(dto.plantId()));
        }
        if (dto.requiredDeposit() != null) {
            request.setRequiredDeposit(dto.requiredDeposit());
        }
        if (dto.productionEstimateWeeks() != null) {
            request.setProductionEstimateWeeks(dto.productionEstimateWeeks());
        }
        if (dto.requestedDeliveryDate() != null) {
            request.setRequestedDeliveryDate(dto.requestedDeliveryDate());
        }
        if (dto.notes() != null) {
            request.setNotes(dto.notes());
        }
        repository.flush();
        audit.record(AuditEvent.of("UPDATE", "FLEET_REQUEST", String.valueOf(id))
                .withActor(actor).withChange(before, auditMapper.json(request)));
        return assembler.toDetail(request, actor.role());
    }

    private String nextRequestNumber() {
        int year = ZonedDateTime.now(clock).withZoneSameInstant(IST).getYear();
        return "FLT-%d-%04d".formatted(year, repository.nextRequestNumber());
    }

    /** The variant with its model, or 404; 409 if it or its model is inactive. */
    @Transactional(readOnly = true)
    public VehicleVariant activeVariant(Long variantId) {
        VehicleVariant variant = variants.findWithModelById(variantId)
                .orElseThrow(() -> new NotFoundException("Vehicle variant " + variantId + " not found."));
        if (!variant.isActive() || !variant.getModel().isActive()) {
            throw new ConflictException("Vehicle variant " + variant.getCode() + " is not active.");
        }
        return variant;
    }

    private Plant plant(Long plantId) {
        if (plantId == null) {
            return null;
        }
        Plant plant = plants.findById(plantId)
                .orElseThrow(() -> new NotFoundException("Plant " + plantId + " not found."));
        if (!plant.isActive()) {
            throw new ConflictException("Plant " + plant.getCode() + " is not active.");
        }
        return plant;
    }
}
