package com.renault.fleet.dispatch.service;

import com.renault.fleet.common.api.PageResponse;
import com.renault.fleet.common.error.NotFoundException;
import com.renault.fleet.dispatch.dto.DispatchDto;
import com.renault.fleet.dispatch.entity.Dispatch;
import com.renault.fleet.dispatch.repository.DispatchRepository;
import com.renault.fleet.fleetrequest.repository.FleetRequestRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DispatchQueryService {

    private final FleetRequestRepository requests;
    private final DispatchRepository dispatches;

    public DispatchQueryService(FleetRequestRepository requests, DispatchRepository dispatches) {
        this.requests = requests;
        this.dispatches = dispatches;
    }

    @Transactional(readOnly = true)
    public PageResponse<DispatchDto> forRequest(Long requestId, Pageable pageable) {
        if (!requests.existsById(requestId)) {
            throw new NotFoundException("Fleet request " + requestId + " not found.");
        }
        return PageResponse.from(dispatches.findByFleetRequestId(requestId, pageable), DispatchQueryService::toDto);
    }

    static DispatchDto toDto(Dispatch d) {
        return new DispatchDto(d.getId(), d.getDispatchNumber(),
                d.getFromLocation() == null ? null : d.getFromLocation().getId(),
                d.getFromLocation() == null ? null : d.getFromLocation().getName(),
                d.getQuantity(), d.getScheduledDate(), d.getDispatchedAt(), d.getDeliveredAt(), d.getCarrier(),
                d.getVehicleReference(), d.getStatus(),
                d.getScheduledBy() == null ? null : d.getScheduledBy().getFullName(), d.getNotes(), d.getCreatedAt());
    }
}
