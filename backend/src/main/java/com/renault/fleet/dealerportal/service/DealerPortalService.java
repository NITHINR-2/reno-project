package com.renault.fleet.dealerportal.service;

import com.renault.fleet.audit.service.AuditEvent;
import com.renault.fleet.audit.service.AuditService;
import com.renault.fleet.catalog.entity.VehicleColour;
import com.renault.fleet.catalog.entity.VehicleVariant;
import com.renault.fleet.catalog.repository.VehicleColourRepository;
import com.renault.fleet.common.api.PageResponse;
import com.renault.fleet.common.error.ApiException;
import com.renault.fleet.inventory.entity.InventoryStock;
import com.renault.fleet.inventory.repository.InventoryStockRepository;
import org.springframework.data.domain.Sort;
import com.renault.fleet.inventory.service.StockAllocationService;
import com.renault.fleet.production.entity.Plant;
import com.renault.fleet.production.repository.PlantRepository;
import com.renault.fleet.workflow.service.WorkflowHistoryService;
import org.springframework.http.HttpStatus;
import com.renault.fleet.common.error.BadRequestException;
import com.renault.fleet.common.error.ConflictException;
import com.renault.fleet.common.error.NotFoundException;
import com.renault.fleet.dealer.entity.Dealer;
import com.renault.fleet.dealer.entity.DealerStatus;
import com.renault.fleet.dealer.repository.DealerRepository;
import com.renault.fleet.dealerportal.dto.CreateDealerOrderRequest;
import com.renault.fleet.dealerportal.dto.DealerPortalDtos;
import com.renault.fleet.dealerportal.dto.DealerPortalFilters;
import com.renault.fleet.dealerportal.entity.DealerOrder;
import com.renault.fleet.dealerportal.mapper.DealerPortalMapper;
import com.renault.fleet.dealerportal.repository.DealerOrderRepository;
import com.renault.fleet.fleetrequest.dto.TransitionRequest;
import com.renault.fleet.fleetrequest.entity.FleetRequest;
import com.renault.fleet.fleetrequest.repository.FleetRequestRepository;
import com.renault.fleet.fleetrequest.service.FleetRequestService;
import com.renault.fleet.payment.entity.PaymentReceipt;
import com.renault.fleet.payment.repository.PaymentProofRepository;
import com.renault.fleet.payment.repository.PaymentReceiptRepository;
import com.renault.fleet.quotation.entity.QuotationStatus;
import com.renault.fleet.quotation.repository.QuotationRepository;
import com.renault.fleet.security.AuthenticatedUser;
import com.renault.fleet.user.repository.UserRepository;
import com.renault.fleet.workflow.entity.WorkflowAction;
import com.renault.fleet.workflow.service.WorkflowService;
import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Dealer orders and the dealer's view of its fleet requests. Every query is scoped to the principal's
 * {@code dealerId}; another dealer's order or request is reported as 404, never 403, so ids can't be probed.
 */
@Service
public class DealerPortalService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    public static final String STOCK_RESERVED = "STOCK_RESERVED";

    private final DealerOrderRepository orders;
    private final FleetRequestRepository requests;
    private final DealerRepository dealers;
    private final UserRepository users;
    private final QuotationRepository quotations;
    private final PaymentReceiptRepository receipts;
    private final PaymentProofRepository proofs;
    private final FleetRequestService fleetRequests;
    private final VehicleColourRepository colours;
    private final PlantRepository plants;
    private final InventoryStockRepository stockRepository;
    private final StockAllocationService stockAllocations;
    private final WorkflowHistoryService history;
    private final WorkflowService workflow;
    private final DealerPortalMapper mapper;
    private final AuditService audit;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public DealerPortalService(DealerOrderRepository orders, FleetRequestRepository requests, DealerRepository dealers,
            UserRepository users, QuotationRepository quotations, PaymentReceiptRepository receipts,
            PaymentProofRepository proofs, FleetRequestService fleetRequests, VehicleColourRepository colours,
            PlantRepository plants, InventoryStockRepository stockRepository, StockAllocationService stockAllocations, WorkflowHistoryService history,
            WorkflowService workflow,
            DealerPortalMapper mapper, AuditService audit, JsonMapper jsonMapper, Clock clock) {
        this.orders = orders;
        this.requests = requests;
        this.dealers = dealers;
        this.users = users;
        this.quotations = quotations;
        this.receipts = receipts;
        this.proofs = proofs;
        this.fleetRequests = fleetRequests;
        this.colours = colours;
        this.plants = plants;
        this.stockRepository = stockRepository;
        this.stockAllocations = stockAllocations;
        this.history = history;
        this.workflow = workflow;
        this.mapper = mapper;
        this.audit = audit;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    /** The principal's dealership id; a DEALER account without one sees nothing. */
    public static Long dealerId(AuthenticatedUser actor) {
        if (actor.dealerId() == null) {
            throw new AccessDeniedException("This account is not linked to a dealer.");
        }
        return actor.dealerId();
    }

    @Transactional
    public DealerPortalDtos.OrderDetail createOrder(CreateDealerOrderRequest body, AuthenticatedUser actor) {
        Dealer dealer = dealers.findById(dealerId(actor)).orElseThrow(() -> new AccessDeniedException("Dealer not found."));
        if (dealer.getStatus() != DealerStatus.ACTIVE) {
            throw new ConflictException("Your dealership is " + dealer.getStatus() + "; only ACTIVE dealers can order.");
        }
        Set<List<Long>> seen = new HashSet<>();
        for (CreateDealerOrderRequest.Line line : body.lines()) {
            if (!seen.add(Arrays.asList(line.variantId(), line.colourId()))) {
                throw new BadRequestException("Variant " + line.variantId() + " appears more than once in the same colour; combine the lines.");
            }
        }
        String notes = body.notes() == null || body.notes().isBlank() ? null : body.notes().strip();
        List<VehicleVariant> variants = new ArrayList<>();
        List<VehicleColour> colourChoices = new ArrayList<>();
        for (CreateDealerOrderRequest.Line line : body.lines()) {
            if (line.expectedFromStock() != null && line.expectedFromStock() > line.quantity()) {
                throw new BadRequestException("expectedFromStock can't exceed the line quantity.");
            }
            VehicleVariant variant = fleetRequests.activeVariant(line.variantId());
            variants.add(variant);
            colourChoices.add(colourFor(variant, line.colourId()));
        }
        Plant plant = body.plantId() == null ? null : activePlant(body.plantId());
        List<List<InventoryStock>> stockRows = plant == null ? null : lockAndCheck(plant, body.lines(), colourChoices);

        DealerOrder order = orders.saveAndFlush(new DealerOrder(nextOrderNumber(), dealer,
                users.getReferenceById(actor.id()), body.requestedDeliveryDate(), notes));
        List<FleetRequest> lines = new ArrayList<>();
        for (int i = 0; i < body.lines().size(); i++) {
            CreateDealerOrderRequest.Line line = body.lines().get(i);
            FleetRequest request = fleetRequests.createForDealerOrder(order, i + 1, variants.get(i), colourChoices.get(i),
                    line.quantity(), actor);
            if (plant != null) {
                request.setPlant(plant);
                int reserved = stockAllocations.reserveFrom(request, stockRows.get(i), actor);
                history.record(request, request.getStatus(), request.getStatus(), STOCK_RESERVED, actor, null,
                        jsonMapper.writeValueAsString(Map.of("plant", plant.getCode(), "reservedUnits", reserved,
                                "productionRequired", request.getProductionRequired())));
            }
            lines.add(request);
        }
        DealerPortalDtos.OrderDetail dto = mapper.toDetail(order, lines);
        audit.record(AuditEvent.of("CREATE", "DEALER_ORDER", String.valueOf(order.getId()))
                .withActor(actor).withChange(null, jsonMapper.writeValueAsString(dto)));
        return dto;
    }

    @Transactional(readOnly = true)
    public PageResponse<DealerPortalDtos.OrderSummary> listOrders(DealerPortalFilters.Orders f, Pageable pageable,
            AuthenticatedUser actor) {
        Long dealerId = dealerId(actor);
        List<Specification<DealerOrder>> specs = new ArrayList<>();
        specs.add((root, q, cb) -> cb.equal(root.get("dealer").get("id"), dealerId));
        if (f.q() != null && !f.q().isBlank()) {
            String like = "%" + escape(f.q()) + "%";
            specs.add((root, q, cb) -> cb.like(cb.lower(root.get("orderNumber")), like, '\\'));
        }
        if (f.from() != null) {
            specs.add((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), f.from().atStartOfDay(IST).toInstant()));
        }
        if (f.to() != null) {
            specs.add((root, q, cb) -> cb.lessThan(root.get("createdAt"), f.to().plusDays(1).atStartOfDay(IST).toInstant()));
        }
        Page<DealerOrder> page = orders.findAll(Specification.allOf(specs), pageable);
        Map<Long, List<FleetRequest>> lines = linesOf(page.getContent().stream().map(DealerOrder::getId).toList());
        return PageResponse.from(page, o -> mapper.toSummary(o, lines.getOrDefault(o.getId(), List.of())));
    }

    @Transactional(readOnly = true)
    public DealerPortalDtos.OrderDetail getOrder(Long id, AuthenticatedUser actor) {
        DealerOrder order = orders.findByIdAndDealerId(id, dealerId(actor))
                .orElseThrow(() -> new NotFoundException("Order " + id + " not found."));
        return mapper.toDetail(order, linesOf(List.of(id)).getOrDefault(id, List.of()));
    }

    @Transactional(readOnly = true)
    public PageResponse<DealerPortalDtos.Request> listRequests(DealerPortalFilters.Requests f, Pageable pageable,
            AuthenticatedUser actor) {
        Long dealerId = dealerId(actor);
        List<Specification<FleetRequest>> specs = new ArrayList<>();
        specs.add((root, q, cb) -> cb.equal(root.get("dealer").get("id"), dealerId));
        if (f.status() != null) {
            specs.add((root, q, cb) -> root.get("status").in(f.status().internal()));
        }
        if (f.q() != null && !f.q().isBlank()) {
            String like = "%" + escape(f.q()) + "%";
            specs.add((root, q, cb) -> cb.like(cb.lower(root.get("requestNumber")), like, '\\'));
        }
        return PageResponse.from(requests.findAll(Specification.allOf(specs), pageable), mapper::toRequest);
    }

    @Transactional(readOnly = true)
    public DealerPortalDtos.RequestDetail getRequest(Long id, AuthenticatedUser actor) {
        FleetRequest request = ownRequest(id, actor);
        DealerPortalDtos.Quotation quotation = quotations
                .findFirstByFleetRequestIdAndStatus(id, QuotationStatus.ISSUED)
                .map(mapper::toQuotation)
                .orElse(null);
        List<PaymentReceipt> own = receipts.findByFleetRequestId(id, Pageable.unpaged()).getContent();
        Set<Long> withProof = own.isEmpty() ? Set.of()
                : Set.copyOf(proofs.findReceiptIdsWithProof(own.stream().map(PaymentReceipt::getId).toList()));
        return new DealerPortalDtos.RequestDetail(mapper.toRequest(request), quotation,
                own.stream().map(p -> mapper.toReceipt(p, withProof.contains(p.getId()))).toList());
    }

    /** Withdraws a request; the workflow allows a DEALER only from PENDING_MARKETING_REVIEW (409 afterwards). */
    @Transactional
    public DealerPortalDtos.RequestDetail cancel(Long id, DealerPortalFilters.Cancel body, AuthenticatedUser actor) {
        ownRequest(id, actor);
        workflow.transition(id, WorkflowAction.CANCEL, new TransitionRequest(body.reason(), body.version()), actor);
        return getRequest(id, actor);
    }

    /** Plants dealers can order from (active ones), by name. */
    @Transactional(readOnly = true)
    public List<DealerPortalDtos.Plant> plants(AuthenticatedUser actor) {
        dealerId(actor);
        return plants.findAll(Sort.by("name")).stream().filter(Plant::isActive)
                .map(p -> new DealerPortalDtos.Plant(p.getId(), p.getCode(), p.getName(), p.getCity(), p.getState()))
                .toList();
    }

    /** Free units per variant and colour at a plant. Display only: the order re-checks under row locks. */
    @Transactional(readOnly = true)
    public DealerPortalDtos.PlantAvailability availability(Long plantId, AuthenticatedUser actor) {
        dealerId(actor);
        Plant plant = activePlant(plantId);
        return new DealerPortalDtos.PlantAvailability(plant.getId(), clock.instant(),
                stockRepository.availabilityAtPlant(plant.getId()).stream()
                        .map(a -> new DealerPortalDtos.Availability(a.getVariantId(), a.getColourId(), a.getAvailable()))
                        .toList());
    }

    /** The caller's own request, or 404. */
    @Transactional(readOnly = true)
    public FleetRequest ownRequest(Long id, AuthenticatedUser actor) {
        return requests.findByIdAndDealerId(id, dealerId(actor))
                .orElseThrow(() -> new NotFoundException("Request " + id + " not found."));
    }

    /**
     * Locks the plant's stock rows for every line (one ascending-id pass) and refuses the order with 409 if any line
     * expected more units from stock than are free now. The 409 lists each short line with its current count.
     */
    private List<List<InventoryStock>> lockAndCheck(Plant plant, List<CreateDealerOrderRequest.Line> lines,
            List<VehicleColour> colourChoices) {
        List<StockAllocationService.StockKey> keys = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            VehicleColour colour = colourChoices.get(i);
            keys.add(new StockAllocationService.StockKey(lines.get(i).variantId(), colour == null ? null : colour.getId()));
        }
        Map<StockAllocationService.StockKey, List<InventoryStock>> locked = stockAllocations.lockPlantStock(plant.getId(), keys);
        List<Map<String, Object>> shortLines = new ArrayList<>();
        List<List<InventoryStock>> rows = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            CreateDealerOrderRequest.Line line = lines.get(i);
            List<InventoryStock> lineRows = locked.get(keys.get(i));
            rows.add(lineRows);
            int available = StockAllocationService.available(lineRows);
            if (line.expectedFromStock() != null && available < line.expectedFromStock()) {
                Map<String, Object> shortLine = new LinkedHashMap<>();
                shortLine.put("lineNo", i + 1);
                shortLine.put("variantId", line.variantId());
                shortLine.put("colourId", keys.get(i).colourId());
                shortLine.put("quantity", line.quantity());
                shortLine.put("expectedFromStock", line.expectedFromStock());
                shortLine.put("available", available);
                shortLines.add(shortLine);
            }
        }
        if (!shortLines.isEmpty()) {
            Map<String, Object> first = shortLines.getFirst();
            throw new ApiException(HttpStatus.CONFLICT, "Stock Changed",
                    "Only " + first.get("available") + " available at " + plant.getName() + " for line " + first.get("lineNo")
                            + " (you expected " + first.get("expectedFromStock") + "). Order what is available from stock "
                            + "and have the rest produced, or change the quantity.")
                    .with("plantId", plant.getId())
                    .with("lines", shortLines);
        }
        return rows;
    }

    private Plant activePlant(Long plantId) {
        Plant plant = plants.findById(plantId).orElseThrow(() -> new NotFoundException("Plant " + plantId + " not found."));
        if (!plant.isActive()) {
            throw new ConflictException("Plant " + plant.getName() + " is not taking orders.");
        }
        return plant;
    }

    private VehicleColour colourFor(VehicleVariant variant, Long colourId) {
        if (colourId == null) {
            return null;
        }
        VehicleColour colour = colours.findById(colourId)
                .filter(c -> c.getModel().getId().equals(variant.getModel().getId()))
                .orElseThrow(() -> new BadRequestException("Colour " + colourId + " is not offered for "
                        + variant.getModel().getName() + "."));
        if (!colour.isActive()) {
            throw new ConflictException("Colour " + colour.getName() + " is no longer available.");
        }
        return colour;
    }

    private Map<Long, List<FleetRequest>> linesOf(List<Long> orderIds) {
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        return requests.findByDealerOrderIdInOrderByDealerOrderLineNo(orderIds).stream()
                .collect(Collectors.groupingBy(r -> r.getDealerOrder().getId(), LinkedHashMap::new, Collectors.toList()));
    }

    private String nextOrderNumber() {
        int year = ZonedDateTime.now(clock).withZoneSameInstant(IST).getYear();
        return "DO-%d-%04d".formatted(year, orders.nextOrderNumber());
    }

    private static String escape(String q) {
        return q.strip().toLowerCase(Locale.ROOT).replace("%", "\\%").replace("_", "\\_");
    }
}
