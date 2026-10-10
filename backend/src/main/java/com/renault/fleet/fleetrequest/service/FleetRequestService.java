package com.renault.fleet.fleetrequest.service;

import com.renault.fleet.common.api.PageResponse;
import com.renault.fleet.fleetrequest.dto.FleetRequestSummaryDto;
import com.renault.fleet.fleetrequest.entity.FleetRequest;
import com.renault.fleet.fleetrequest.entity.FleetRequestStatus;
import com.renault.fleet.fleetrequest.mapper.FleetRequestMapper;
import com.renault.fleet.fleetrequest.repository.FleetRequestRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Phase 1: read-only listing used to prove role enforcement. Workflow and editing arrive in later phases. */
@Service
@Transactional(readOnly = true)
public class FleetRequestService {

    private final FleetRequestRepository repository;
    private final FleetRequestMapper mapper;

    public FleetRequestService(FleetRequestRepository repository, FleetRequestMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public PageResponse<FleetRequestSummaryDto> list(FleetRequestStatus status, Pageable pageable) {
        Page<FleetRequest> page = status == null
                ? repository.findAllBy(pageable)
                : repository.findByStatus(status, pageable);
        return PageResponse.from(page, mapper::toSummary);
    }
}
