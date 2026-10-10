package com.renault.fleet.dispatch.controller;

import com.renault.fleet.dispatch.dto.ScheduleDispatchRequest;
import com.renault.fleet.dispatch.service.DispatchService;
import com.renault.fleet.fleetrequest.dto.FleetRequestDetailDto;
import com.renault.fleet.fleetrequest.dto.TransitionRequest;
import com.renault.fleet.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

    public DispatchController(DispatchService service) {
        this.service = service;
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
