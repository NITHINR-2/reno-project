package com.renault.fleet.production.controller;

import com.renault.fleet.fleetrequest.dto.FleetRequestDetailDto;
import com.renault.fleet.production.dto.ScheduleProductionRequest;
import com.renault.fleet.production.service.ProductionService;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Supervisor: production and dispatch")
public class ProductionController {

    private final ProductionService service;

    public ProductionController(ProductionService service) {
        this.service = service;
    }

    @PostMapping("/api/fleet-requests/{id}/schedule-production")
    @PreAuthorize("hasAuthority('PRODUCTION_SCHEDULE')")
    @Operation(summary = "Schedule production of productionRequired units: FINANCIALLY_CLEARED → PRODUCTION_SCHEDULED",
            description = "409 when productionRequired = 0; schedule dispatch directly instead.")
    public FleetRequestDetailDto schedule(@PathVariable Long id, @Valid @RequestBody ScheduleProductionRequest body,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal) {
        return service.schedule(id, body, principal);
    }
}
