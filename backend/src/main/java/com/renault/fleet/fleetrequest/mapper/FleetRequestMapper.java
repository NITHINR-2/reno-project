package com.renault.fleet.fleetrequest.mapper;

import com.renault.fleet.catalog.entity.VehicleVariant;
import com.renault.fleet.dealer.entity.Dealer;
import com.renault.fleet.fleetrequest.dto.FleetRequestDetailDto;
import com.renault.fleet.fleetrequest.dto.FleetRequestDetailDto.DealerRef;
import com.renault.fleet.fleetrequest.dto.FleetRequestDetailDto.PlantRef;
import com.renault.fleet.fleetrequest.dto.FleetRequestDetailDto.UserRef;
import com.renault.fleet.fleetrequest.dto.FleetRequestDetailDto.VariantRef;
import com.renault.fleet.fleetrequest.dto.FleetRequestSummaryDto;
import com.renault.fleet.fleetrequest.entity.FleetRequest;
import com.renault.fleet.inventory.dto.StockAllocationDto;
import com.renault.fleet.production.entity.Plant;
import com.renault.fleet.user.entity.User;
import com.renault.fleet.workflow.entity.WorkflowAction;
import com.renault.fleet.sla.service.SlaService;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class FleetRequestMapper {

    private final SlaService sla;

    public FleetRequestMapper(SlaService sla) {
        this.sla = sla;
    }

    /** Expects dealer (+region), variant (+model) and plant to be fetched (see the repository's entity graphs). */
    public FleetRequestSummaryDto toSummary(FleetRequest request) {
        Plant plant = request.getPlant();
        return new FleetRequestSummaryDto(
                request.getId(),
                request.getRequestNumber(),
                request.getDealer().getId(),
                request.getDealer().getName(),
                request.getDealer().getRegion().getName(),
                request.getVariant().getModel().getName(),
                request.getVariant().getName(),
                plant == null ? null : plant.getName(),
                request.getQuantity(),
                request.getAllocatedQty(),
                request.getProductionRequired(),
                request.getRequestedDeliveryDate(),
                request.getStatus(),
                request.getStageEnteredAt(),
                request.getSlaDueAt(),
                sla.statusOf(request),
                request.getVersion(),
                request.getCreatedAt(),
                request.getUpdatedAt());
    }

    public FleetRequestDetailDto toDetail(FleetRequest r, List<StockAllocationDto> allocations, long eligibleStock,
            BigDecimal depositReceived, List<WorkflowAction> allowedActions) {
        Dealer dealer = r.getDealer();
        VehicleVariant variant = r.getVariant();
        Plant plant = r.getPlant();
        return new FleetRequestDetailDto(
                r.getId(),
                r.getRequestNumber(),
                r.getStatus(),
                r.getStageEnteredAt(),
                r.getSlaWarningAt(),
                r.getSlaDueAt(),
                sla.statusOf(r),
                new DealerRef(dealer.getId(), dealer.getDealerCode(), dealer.getName(), dealer.getRegion().getId(),
                        dealer.getRegion().getName()),
                new VariantRef(variant.getId(), variant.getCode(), variant.getName(), variant.getModel().getId(),
                        variant.getModel().getName(), variant.getUnitPrice()),
                plant == null ? null : new PlantRef(plant.getId(), plant.getCode(), plant.getName()),
                r.getQuantity(),
                r.getAllocatedQty(),
                r.getProductionRequired(),
                eligibleStock,
                r.getRequiredDeposit(),
                depositReceived,
                r.getProductionEstimateWeeks(),
                r.getRequestedDeliveryDate(),
                r.getNotes(),
                r.getReturnReason(),
                r.getCancelReason(),
                userRef(r.getCreatedBy()),
                userRef(r.getAssignedTo()),
                r.getStockMappedAt(),
                allocations,
                allowedActions,
                r.getVersion(),
                r.getCreatedAt(),
                r.getUpdatedAt());
    }

    private static UserRef userRef(User user) {
        return user == null ? null : new UserRef(user.getId(), user.getFullName());
    }
}
