package com.renault.fleet.fleetrequest.dto;

import com.renault.fleet.fleetrequest.entity.FleetRequestStatus;
import com.renault.fleet.inventory.dto.StockAllocationDto;
import com.renault.fleet.workflow.entity.WorkflowAction;
import java.math.BigDecimal;
import com.renault.fleet.sla.entity.SlaStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Full view of one request. {@code allowedActions} is what the caller's role may do next (guards included);
 * it is a UX hint only, the server re-checks every action.
 */
public record FleetRequestDetailDto(
        Long id,
        String requestNumber,
        FleetRequestStatus status,
        Instant stageEnteredAt,
        Instant slaWarningAt,
        Instant slaDueAt,
        SlaStatus slaStatus,
        DealerRef dealer,
        VariantRef variant,
        PlantRef plant,
        int quantity,
        int allocatedQty,
        int productionRequired,
        long eligibleStockAvailable,
        BigDecimal requiredDeposit,
        BigDecimal depositReceived,
        Integer productionEstimateWeeks,
        LocalDate requestedDeliveryDate,
        String notes,
        String returnReason,
        String cancelReason,
        UserRef createdBy,
        UserRef assignedTo,
        Instant stockMappedAt,
        List<StockAllocationDto> allocations,
        List<WorkflowAction> allowedActions,
        Long version,
        Instant createdAt,
        Instant updatedAt) {

    public record DealerRef(Long id, String code, String name, Long regionId, String regionName) {
    }

    public record VariantRef(Long id, String code, String name, Long modelId, String modelName, BigDecimal unitPrice) {
    }

    public record PlantRef(Long id, String code, String name) {
    }

    public record UserRef(Long id, String fullName) {
    }
}
