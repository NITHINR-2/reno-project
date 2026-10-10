package com.renault.fleet.inventory.service;

import com.renault.fleet.audit.service.AuditEvent;
import com.renault.fleet.audit.service.AuditService;
import com.renault.fleet.common.error.BadRequestException;
import com.renault.fleet.common.error.ConflictException;
import com.renault.fleet.common.error.NotFoundException;
import com.renault.fleet.common.persistence.Versions;
import com.renault.fleet.fleetrequest.dto.FleetRequestDetailDto;
import com.renault.fleet.fleetrequest.entity.FleetRequest;
import com.renault.fleet.fleetrequest.entity.FleetRequestStatus;
import com.renault.fleet.fleetrequest.mapper.FleetRequestAuditMapper;
import com.renault.fleet.fleetrequest.repository.FleetRequestRepository;
import com.renault.fleet.fleetrequest.service.FleetRequestDetailAssembler;
import com.renault.fleet.inventory.dto.MapStockRequest;
import com.renault.fleet.inventory.entity.AllocationStatus;
import com.renault.fleet.inventory.entity.InventoryStock;
import com.renault.fleet.inventory.entity.StockAllocation;
import com.renault.fleet.inventory.repository.InventoryStockRepository;
import com.renault.fleet.inventory.repository.StockAllocationRepository;
import com.renault.fleet.security.AuthenticatedUser;
import com.renault.fleet.user.entity.User;
import com.renault.fleet.user.repository.UserRepository;
import com.renault.fleet.workflow.service.WorkflowHistoryService;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Maps inventory stock to fleet requests. Every stock change happens inside one transaction that holds
 * {@code SELECT ... FOR UPDATE} locks on the request row and on each touched stock row (taken in ascending id
 * order, so two mappings can never deadlock). {@code CHECK (quantity_available >= 0)} is the last line of defence.
 */
@Service
public class StockAllocationService {

    public static final String STOCK_MAPPED = "STOCK_MAPPED";

    private final FleetRequestRepository requests;
    private final InventoryStockRepository stock;
    private final StockAllocationRepository allocations;
    private final UserRepository users;
    private final WorkflowHistoryService history;
    private final AuditService audit;
    private final FleetRequestAuditMapper auditMapper;
    private final FleetRequestDetailAssembler assembler;
    private final Clock clock;

    public StockAllocationService(FleetRequestRepository requests, InventoryStockRepository stock,
            StockAllocationRepository allocations, UserRepository users, WorkflowHistoryService history,
            AuditService audit, FleetRequestAuditMapper auditMapper, FleetRequestDetailAssembler assembler, Clock clock) {
        this.requests = requests;
        this.stock = stock;
        this.allocations = allocations;
        this.users = users;
        this.history = history;
        this.audit = audit;
        this.auditMapper = auditMapper;
        this.assembler = assembler;
        this.clock = clock;
    }

    /** Replaces the request's active allocations with {@code body.lines()} and recomputes production. */
    @Transactional
    public FleetRequestDetailDto mapStock(Long requestId, MapStockRequest body, AuthenticatedUser actor) {
        FleetRequest request = requests.findByIdForUpdate(requestId)
                .orElseThrow(() -> new NotFoundException("Fleet request " + requestId + " not found."));
        Versions.check(request, body.version());
        if (request.getStatus() != FleetRequestStatus.PENDING_MARKETING_REVIEW) {
            throw new ConflictException("Stock can only be mapped while the request is PENDING_MARKETING_REVIEW (it is "
                    + request.getStatus() + ").");
        }
        Map<Long, Integer> lines = validateLines(body.lines(), request.getQuantity());
        String before = auditMapper.json(request);
        Instant now = Instant.now(clock);

        Set<Long> touched = new TreeSet<>(allocations.findActiveStockIds(requestId));
        touched.addAll(lines.keySet());
        Map<Long, InventoryStock> locked = lockInOrder(touched);
        release(requestId, locked, now);

        User actorRef = users.getReferenceById(actor.id());
        int total = 0;
        for (Map.Entry<Long, Integer> line : lines.entrySet()) {
            InventoryStock row = locked.get(line.getKey());
            int qty = line.getValue();
            if (!row.getVariant().getId().equals(request.getVariant().getId())) {
                throw new ConflictException("Stock " + row.getId() + " holds a different variant than the request.");
            }
            if (!row.getLocation().isActive()) {
                throw new ConflictException("Stock " + row.getId() + " is at an inactive location.");
            }
            if (row.getQuantityAvailable() < qty) {
                throw new ConflictException("Insufficient stock at " + row.getLocation().getName() + ": requested " + qty
                        + ", available " + row.getQuantityAvailable() + ".");
            }
            row.setQuantityAvailable(row.getQuantityAvailable() - qty);
            row.setQuantityAllocated(row.getQuantityAllocated() + qty);
            allocations.save(new StockAllocation(request, row, qty, actorRef));
            total += qty;
        }
        request.applyAllocation(total);
        request.markStockMapped(actorRef, now);

        String metadata = auditMapper.toJson(Map.of("lines", lines.entrySet().stream()
                .map(e -> Map.of("stockId", e.getKey(), "quantity", e.getValue())).toList(),
                "allocatedQty", total, "productionRequired", request.getProductionRequired()));
        history.record(request, request.getStatus(), request.getStatus(), STOCK_MAPPED, actor, null, metadata);
        requests.flush();
        audit.record(AuditEvent.of(STOCK_MAPPED, "FLEET_REQUEST", String.valueOf(requestId))
                .withActor(actor).withChange(before, auditMapper.json(request)));
        return assembler.toDetail(request, actor.role());
    }

    /**
     * Releases every active allocation of a request back to stock. The caller must hold the request row lock and
     * be in a transaction (cancel, variant change).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int releaseAll(FleetRequest request) {
        Map<Long, InventoryStock> locked = lockInOrder(new TreeSet<>(allocations.findActiveStockIds(request.getId())));
        int released = release(request.getId(), locked, Instant.now(clock));
        request.applyAllocation(0);
        return released;
    }

    private int release(Long requestId, Map<Long, InventoryStock> locked, Instant now) {
        List<StockAllocation> active = allocations.findByFleetRequestIdAndStatusOrderById(requestId, AllocationStatus.ACTIVE);
        int released = 0;
        for (StockAllocation allocation : active) {
            InventoryStock row = locked.get(allocation.getStock().getId());
            row.setQuantityAvailable(row.getQuantityAvailable() + allocation.getQuantity());
            row.setQuantityAllocated(row.getQuantityAllocated() - allocation.getQuantity());
            allocation.setStatus(AllocationStatus.RELEASED);
            allocation.setReleasedAt(now);
            released += allocation.getQuantity();
        }
        // Hibernate runs inserts before updates; flush now so re-allocating the same stock row doesn't collide
        // with the still-ACTIVE row in ux_stock_allocations_active.
        allocations.flush();
        return released;
    }

    /** Locks stock rows in ascending id order (the caller passes a sorted set) before they are read anywhere else. */
    private Map<Long, InventoryStock> lockInOrder(Collection<Long> sortedIds) {
        Map<Long, InventoryStock> locked = new LinkedHashMap<>();
        for (Long id : sortedIds) {
            locked.put(id, stock.findByIdForUpdate(id)
                    .orElseThrow(() -> new NotFoundException("Stock " + id + " not found.")));
        }
        return locked;
    }

    private static Map<Long, Integer> validateLines(List<MapStockRequest.Line> lines, int requestQuantity) {
        Map<Long, Integer> result = new LinkedHashMap<>();
        Set<Long> seen = new HashSet<>();
        int total = 0;
        for (MapStockRequest.Line line : lines) {
            if (!seen.add(line.stockId())) {
                throw new BadRequestException("Stock " + line.stockId() + " appears more than once.");
            }
            result.put(line.stockId(), line.quantity());
            total += line.quantity();
        }
        if (total > requestQuantity) {
            throw new ConflictException("Cannot allocate " + total + " units to a request for " + requestQuantity + ".");
        }
        return result;
    }
}
