package com.renault.fleet.fleetrequest.controller;

import com.renault.fleet.common.api.PageResponse;
import com.renault.fleet.fleetrequest.dto.FleetRequestSummaryDto;
import com.renault.fleet.fleetrequest.entity.FleetRequestStatus;
import com.renault.fleet.fleetrequest.service.FleetRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/fleet-requests")
@Tag(name = "Fleet requests")
public class FleetRequestController {

    private final FleetRequestService service;

    public FleetRequestController(FleetRequestService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('FLEET_REQUEST_READ')")
    @Operation(summary = "List fleet requests (paginated, filterable by status)")
    public PageResponse<FleetRequestSummaryDto> list(
            @RequestParam(required = false) FleetRequestStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.list(status, pageable);
    }
}
