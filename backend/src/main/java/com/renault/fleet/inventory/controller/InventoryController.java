package com.renault.fleet.inventory.controller;

import com.renault.fleet.common.api.PageResponse;
import com.renault.fleet.inventory.dto.InventoryLocationDto;
import com.renault.fleet.inventory.dto.InventoryLocationFilter;
import com.renault.fleet.inventory.dto.InventoryStockDto;
import com.renault.fleet.inventory.dto.InventoryStockFilter;
import com.renault.fleet.inventory.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory")
@Tag(name = "Inventory")
public class InventoryController {

    private final InventoryService service;

    public InventoryController(InventoryService service) {
        this.service = service;
    }

    @GetMapping("/locations")
    @PreAuthorize("hasAuthority('FLEET_REQUEST_READ')")
    @Operation(summary = "List inventory locations (filter by type, region, plant, active)")
    public PageResponse<InventoryLocationDto> locations(@ParameterObject InventoryLocationFilter filter,
            @ParameterObject @PageableDefault(size = 20, sort = "code") Pageable pageable) {
        return service.locations(filter, pageable);
    }

    @GetMapping("/stock")
    @PreAuthorize("hasAuthority('FLEET_REQUEST_READ')")
    @Operation(summary = "List stock levels (filter by variant, model, location, location type, availability)")
    public PageResponse<InventoryStockDto> stock(@ParameterObject InventoryStockFilter filter,
            @ParameterObject @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return service.stock(filter, pageable);
    }
}
