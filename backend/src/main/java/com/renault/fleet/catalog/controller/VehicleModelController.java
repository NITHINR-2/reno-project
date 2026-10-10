package com.renault.fleet.catalog.controller;

import com.renault.fleet.catalog.dto.VehicleModelDto;
import com.renault.fleet.catalog.dto.VehicleModelFilter;
import com.renault.fleet.catalog.service.VehicleModelService;
import com.renault.fleet.common.api.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/vehicle-models")
@Tag(name = "Vehicle catalogue")
public class VehicleModelController {

    private final VehicleModelService service;

    public VehicleModelController(VehicleModelService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('FLEET_REQUEST_READ')")
    @Operation(summary = "List vehicle models with their variants (filter by text, category, active)")
    public PageResponse<VehicleModelDto> list(@ParameterObject VehicleModelFilter filter,
            @ParameterObject @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return service.list(filter, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('FLEET_REQUEST_READ')")
    @Operation(summary = "Get one vehicle model with its variants")
    public VehicleModelDto get(@PathVariable Long id) {
        return service.get(id);
    }
}
