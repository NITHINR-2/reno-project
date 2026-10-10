package com.renault.fleet.production.controller;

import com.renault.fleet.common.api.PageResponse;
import com.renault.fleet.production.dto.PlantDto;
import com.renault.fleet.production.service.ProductionQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only reference list for filters and forms. */
@RestController
@Tag(name = "Reference data")
public class PlantController {

    private final ProductionQueryService service;

    public PlantController(ProductionQueryService service) {
        this.service = service;
    }

    @GetMapping("/api/plants")
    @PreAuthorize("hasAuthority('FLEET_REQUEST_READ')")
    @Operation(summary = "List plants")
    public PageResponse<PlantDto> list(@ParameterObject @PageableDefault(size = 100, sort = "name") Pageable pageable) {
        return service.plants(pageable);
    }
}
