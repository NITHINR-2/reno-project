package com.renault.fleet.fleetrequest.mapper;

import com.renault.fleet.catalog.entity.VehicleVariant;
import com.renault.fleet.fleetrequest.entity.FleetRequest;
import com.renault.fleet.fleetrequest.entity.FleetRequestStatus;
import com.renault.fleet.production.entity.Plant;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Serialises a fleet request's business fields for {@code audit_logs.before_json/after_json}. */
@Component
public class FleetRequestAuditMapper {

    /** Only ids of associations, so no lazy loading is triggered. */
    public record Snapshot(
            Long id,
            String requestNumber,
            FleetRequestStatus status,
            Long dealerId,
            Long variantId,
            Long plantId,
            int quantity,
            int allocatedQty,
            int productionRequired,
            BigDecimal requiredDeposit,
            Integer productionEstimateWeeks,
            LocalDate requestedDeliveryDate,
            String notes,
            String returnReason,
            String cancelReason,
            Instant stockMappedAt,
            Long version) {
    }

    private final JsonMapper jsonMapper;

    public FleetRequestAuditMapper(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public Snapshot snapshot(FleetRequest r) {
        VehicleVariant variant = r.getVariant();
        Plant plant = r.getPlant();
        return new Snapshot(r.getId(), r.getRequestNumber(), r.getStatus(), r.getDealer().getId(),
                variant == null ? null : variant.getId(), plant == null ? null : plant.getId(),
                r.getQuantity(), r.getAllocatedQty(), r.getProductionRequired(), r.getRequiredDeposit(),
                r.getProductionEstimateWeeks(), r.getRequestedDeliveryDate(), r.getNotes(), r.getReturnReason(),
                r.getCancelReason(), r.getStockMappedAt(), r.getVersion());
    }

    public String json(FleetRequest request) {
        return toJson(snapshot(request));
    }

    public String toJson(Object value) {
        return value == null ? null : jsonMapper.writeValueAsString(value);
    }
}
