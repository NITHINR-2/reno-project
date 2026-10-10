package com.renault.fleet.workflow.service;

import com.renault.fleet.dispatch.entity.Dispatch;
import com.renault.fleet.dispatch.entity.DispatchStatus;
import com.renault.fleet.dispatch.repository.DispatchRepository;
import com.renault.fleet.fleetrequest.entity.FleetRequest;
import com.renault.fleet.inventory.service.StockAllocationService;
import com.renault.fleet.production.entity.ProductionOrder;
import com.renault.fleet.production.entity.ProductionStatus;
import com.renault.fleet.production.repository.ProductionOrderRepository;
import com.renault.fleet.quotation.entity.Quotation;
import com.renault.fleet.quotation.entity.QuotationStatus;
import com.renault.fleet.quotation.repository.QuotationRepository;
import com.renault.fleet.workflow.entity.WorkflowAction;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Side effects of transitions on other features, run inside the transition's transaction (the request row is
 * locked). Returns metadata for the history row, or an empty map.
 */
@Component
public class TransitionEffects {

    private final StockAllocationService stockAllocations;
    private final QuotationRepository quotations;
    private final ProductionOrderRepository productionOrders;
    private final DispatchRepository dispatches;
    private final Clock clock;

    public TransitionEffects(StockAllocationService stockAllocations, QuotationRepository quotations,
            ProductionOrderRepository productionOrders, DispatchRepository dispatches, Clock clock) {
        this.stockAllocations = stockAllocations;
        this.quotations = quotations;
        this.productionOrders = productionOrders;
        this.dispatches = dispatches;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Map<String, Object> apply(FleetRequest request, WorkflowAction action, String reason) {
        return switch (action) {
            case REJECT -> {
                request.setReturnReason(reason);
                yield Map.of();
            }
            case CANCEL -> cancel(request, reason);
            case COMPLETE -> complete(request);
            default -> Map.of();
        };
    }

    /** Releases stock and voids open documents. Approved money stays recorded (refunds are out of scope). */
    private Map<String, Object> cancel(FleetRequest request, String reason) {
        request.setCancelReason(reason);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("releasedUnits", stockAllocations.releaseAll(request));
        int voided = 0;
        for (QuotationStatus status : EnumSet.of(QuotationStatus.DRAFT, QuotationStatus.ISSUED)) {
            for (Quotation q : quotations.findByFleetRequestIdAndStatus(request.getId(), status)) {
                q.setStatus(QuotationStatus.CANCELLED);
                voided++;
            }
        }
        meta.put("cancelledQuotations", voided);
        int orders = 0;
        for (ProductionOrder order : productionOrders.findByFleetRequestIdAndStatusIn(request.getId(),
                EnumSet.of(ProductionStatus.SCHEDULED, ProductionStatus.IN_PROGRESS))) {
            order.setStatus(ProductionStatus.CANCELLED);
            orders++;
        }
        meta.put("cancelledProductionOrders", orders);
        int shipments = 0;
        for (Dispatch dispatch : dispatches.findByFleetRequestIdAndStatusIn(request.getId(),
                EnumSet.of(DispatchStatus.SCHEDULED))) {
            dispatch.setStatus(DispatchStatus.CANCELLED);
            shipments++;
        }
        meta.put("cancelledDispatches", shipments);
        return meta;
    }

    /** Delivery: production done, dispatch delivered, allocated yard stock consumed. */
    private Map<String, Object> complete(FleetRequest request) {
        Instant now = Instant.now(clock);
        for (ProductionOrder order : productionOrders.findByFleetRequestIdAndStatusIn(request.getId(),
                EnumSet.of(ProductionStatus.SCHEDULED, ProductionStatus.IN_PROGRESS))) {
            order.setStatus(ProductionStatus.COMPLETED);
            order.setCompletedAt(now);
        }
        for (Dispatch dispatch : dispatches.findByFleetRequestIdAndStatusIn(request.getId(),
                EnumSet.of(DispatchStatus.SCHEDULED, DispatchStatus.DISPATCHED))) {
            if (dispatch.getDispatchedAt() == null) {
                dispatch.setDispatchedAt(now);
            }
            dispatch.setDeliveredAt(now);
            dispatch.setStatus(DispatchStatus.DELIVERED);
        }
        return Map.of("consumedStockUnits", stockAllocations.consumeAll(request));
    }
}
