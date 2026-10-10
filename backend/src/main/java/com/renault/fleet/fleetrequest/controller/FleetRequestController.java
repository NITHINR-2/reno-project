package com.renault.fleet.fleetrequest.controller;

import com.renault.fleet.common.api.PageResponse;
import com.renault.fleet.fleetrequest.dto.CreateFleetRequestDto;
import com.renault.fleet.fleetrequest.dto.FleetRequestDetailDto;
import com.renault.fleet.fleetrequest.dto.FleetRequestMetricsDto;
import com.renault.fleet.fleetrequest.service.FleetRequestMetricsService;
import com.renault.fleet.fleetrequest.dto.FleetRequestFilter;
import com.renault.fleet.fleetrequest.dto.FleetRequestSummaryDto;
import com.renault.fleet.fleetrequest.dto.UpdateFleetRequestDto;
import com.renault.fleet.fleetrequest.service.FleetRequestService;
import com.renault.fleet.security.AuthenticatedUser;
import com.renault.fleet.workflow.dto.WorkflowHistoryDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/fleet-requests")
@Tag(name = "Fleet requests")
public class FleetRequestController {

    private final FleetRequestService service;
    private final FleetRequestMetricsService metrics;

    public FleetRequestController(FleetRequestService service, FleetRequestMetricsService metrics) {
        this.service = service;
        this.metrics = metrics;
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('FLEET_REQUEST_READ')")
    @Operation(summary = "Dashboard summary: requests per stage and units still to produce (computed in SQL)")
    public FleetRequestMetricsDto summary() {
        return metrics.summary();
    }

    @GetMapping
    @PreAuthorize("hasAuthority('FLEET_REQUEST_READ')")
    @Operation(summary = "List fleet requests (paginated; filter by status, dealer, region, variant, model, plant, text, dates)")
    public PageResponse<FleetRequestSummaryDto> list(@ParameterObject FleetRequestFilter filter,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.list(filter, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('FLEET_REQUEST_READ')")
    @Operation(summary = "Get one fleet request, with allocations, deposits and the actions available to you")
    public FleetRequestDetailDto get(@PathVariable Long id,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal) {
        return service.get(id, principal);
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("hasAuthority('FLEET_REQUEST_READ')")
    @Operation(summary = "Workflow history of a fleet request (oldest first)")
    public PageResponse<WorkflowHistoryDto> history(@PathVariable Long id,
            @ParameterObject @PageableDefault(size = 50, sort = {"createdAt", "id"}) Pageable pageable) {
        return service.history(id, pageable);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('FLEET_REQUEST_WRITE')")
    @Operation(summary = "Create a fleet request (starts in PENDING_MARKETING_REVIEW)")
    public ResponseEntity<FleetRequestDetailDto> create(@Valid @RequestBody CreateFleetRequestDto body,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal) {
        FleetRequestDetailDto created = service.create(body, principal);
        return ResponseEntity.created(URI.create("/api/fleet-requests/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('FLEET_REQUEST_WRITE')")
    @Operation(summary = "Edit a fleet request in PENDING_MARKETING_REVIEW (null fields are left unchanged)")
    public FleetRequestDetailDto update(@PathVariable Long id, @Valid @RequestBody UpdateFleetRequestDto body,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal) {
        return service.update(id, body, principal);
    }
}
