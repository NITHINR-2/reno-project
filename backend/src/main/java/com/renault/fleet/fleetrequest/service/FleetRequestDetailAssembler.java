package com.renault.fleet.fleetrequest.service;

import com.renault.fleet.common.error.NotFoundException;
import com.renault.fleet.fleetrequest.dto.FleetRequestDetailDto;
import com.renault.fleet.fleetrequest.entity.FleetRequest;
import com.renault.fleet.fleetrequest.mapper.FleetRequestMapper;
import com.renault.fleet.fleetrequest.repository.FleetRequestRepository;
import com.renault.fleet.inventory.entity.AllocationStatus;
import com.renault.fleet.inventory.mapper.InventoryMapper;
import com.renault.fleet.inventory.repository.InventoryStockRepository;
import com.renault.fleet.inventory.repository.StockAllocationRepository;
import com.renault.fleet.payment.entity.ReceiptStatus;
import com.renault.fleet.payment.entity.ReceiptType;
import com.renault.fleet.payment.repository.PaymentReceiptRepository;
import com.renault.fleet.user.entity.RoleCode;
import com.renault.fleet.workflow.service.WorkflowFacts;
import com.renault.fleet.workflow.service.WorkflowTransitions;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Builds {@link FleetRequestDetailDto} inside the caller's transaction (associations are lazy). */
@Component
public class FleetRequestDetailAssembler {

    private final FleetRequestRepository requests;
    private final StockAllocationRepository allocations;
    private final InventoryStockRepository stock;
    private final PaymentReceiptRepository receipts;
    private final FleetRequestMapper mapper;
    private final InventoryMapper inventoryMapper;
    private final WorkflowFacts facts;

    public FleetRequestDetailAssembler(FleetRequestRepository requests, StockAllocationRepository allocations,
            InventoryStockRepository stock, PaymentReceiptRepository receipts, FleetRequestMapper mapper,
            InventoryMapper inventoryMapper, WorkflowFacts facts) {
        this.requests = requests;
        this.allocations = allocations;
        this.stock = stock;
        this.receipts = receipts;
        this.mapper = mapper;
        this.inventoryMapper = inventoryMapper;
        this.facts = facts;
    }

    @Transactional(readOnly = true)
    public FleetRequestDetailDto byId(Long id, RoleCode viewerRole) {
        return toDetail(requests.findDetailedById(id)
                .orElseThrow(() -> new NotFoundException("Fleet request " + id + " not found.")), viewerRole);
    }

    /**
     * Must run inside a transaction. Flushes first so a just-modified request reports its new version and
     * timestamps.
     */
    public FleetRequestDetailDto toDetail(FleetRequest request, RoleCode viewerRole) {
        requests.flush();
        var active = allocations.findByFleetRequestIdAndStatusOrderById(request.getId(), AllocationStatus.ACTIVE)
                .stream().map(inventoryMapper::toAllocation).toList();
        long eligible = stock.sumEligibleAvailable(request.getVariant().getId(),
                request.getColourOption() == null ? null : request.getColourOption().getId());
        var depositReceived = receipts.sumAmount(request.getId(), ReceiptType.DEPOSIT, ReceiptStatus.APPROVED);
        return mapper.toDetail(request, active, eligible, depositReceived,
                WorkflowTransitions.available(request, viewerRole, facts));
    }
}
