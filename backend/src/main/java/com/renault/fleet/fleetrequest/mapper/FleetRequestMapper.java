package com.renault.fleet.fleetrequest.mapper;

import com.renault.fleet.fleetrequest.dto.FleetRequestSummaryDto;
import com.renault.fleet.fleetrequest.entity.FleetRequest;
import org.springframework.stereotype.Component;

@Component
public class FleetRequestMapper {

    /** Expects dealer and variant to be fetched (see the repository's entity graphs). */
    public FleetRequestSummaryDto toSummary(FleetRequest request) {
        return new FleetRequestSummaryDto(
                request.getId(),
                request.getRequestNumber(),
                request.getDealer().getName(),
                request.getVariant().getName(),
                request.getQuantity(),
                request.getAllocatedQty(),
                request.getProductionRequired(),
                request.getStatus(),
                request.getCreatedAt(),
                request.getUpdatedAt());
    }
}
