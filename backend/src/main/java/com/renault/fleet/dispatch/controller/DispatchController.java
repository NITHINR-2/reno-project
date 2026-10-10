package com.renault.fleet.dispatch.controller;

import com.renault.fleet.common.api.PageResponse;
import com.renault.fleet.dispatch.dto.DispatchDto;
import com.renault.fleet.dispatch.dto.ScheduleDispatchRequest;
import com.renault.fleet.dispatch.service.DispatchQueryService;
import com.renault.fleet.dispatch.service.DispatchService;
import com.renault.fleet.fleetrequest.dto.FleetRequestDetailDto;
import com.renault.fleet.fleetrequest.dto.TransitionRequest;
import com.renault.fleet.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/fleet-requests/{id}")
@Tag(name = "Supervisor: production and dispatch")
public class DispatchController {

    private final DispatchService service;
    private final DispatchQueryService queries;

    public DispatchController(DispatchService service, DispatchQueryService queries) {
        this.service = service;
        this.queries = queries;
    }

    @GetMapping("/dispatches")
    @PreAuthorize("hasAuthority('FLEET_REQUEST_READ')")
    @Operation(summary = "Dispatches of a fleet request (newest first)")
    public PageResponse<DispatchDto> list(@PathVariable Long id,
            @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return queries.forRequest(id, pageable);
    }

    @PostMapping("/schedule-dispatch")
    @PreAuthorize("hasAuthority('DISPATCH_SCHEDULE')")
    @Operation(summary = "Schedule dispatch: PRODUCTION_SCHEDULED (or FINANCIALLY_CLEARED with nothing to produce) → DISPATCH_SCHEDULED")
    public FleetRequestDetailDto schedule(@PathVariable Long id, @Valid @RequestBody ScheduleDispatchRequest body,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal) {
        return service.schedule(id, body, principal);
    }

    @PostMapping("/complete")
    @PreAuthorize("hasAuthority('DISPATCH_SCHEDULE')")
    @Operation(summary = "Complete delivery: DISPATCH_SCHEDULED → COMPLETED (allocated stock consumed, orders closed)")
    public FleetRequestDetailDto complete(@PathVariable Long id, @Valid @RequestBody(required = false) TransitionRequest body,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal) {
        return service.complete(id, body, principal);
    }
}
