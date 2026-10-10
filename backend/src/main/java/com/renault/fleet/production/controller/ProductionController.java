package com.renault.fleet.production.controller;

import com.renault.fleet.fleetrequest.dto.FleetRequestDetailDto;
import com.renault.fleet.common.api.PageResponse;
import com.renault.fleet.production.dto.ProductionOrderDto;
import com.renault.fleet.production.dto.ScheduleProductionRequest;
import com.renault.fleet.production.service.ProductionQueryService;
import com.renault.fleet.production.service.ProductionService;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Supervisor: production and dispatch")
public class ProductionController {

    private final ProductionService service;
    private final ProductionQueryService queries;

    public ProductionController(ProductionService service, ProductionQueryService queries) {
        this.service = service;
        this.queries = queries;
    }

    @GetMapping("/api/fleet-requests/{id}/production-orders")
    @PreAuthorize("hasAuthority('FLEET_REQUEST_READ')")
    @Operation(summary = "Production orders of a fleet request (newest first)")
    public PageResponse<ProductionOrderDto> list(@PathVariable Long id,
            @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return queries.forRequest(id, pageable);
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
