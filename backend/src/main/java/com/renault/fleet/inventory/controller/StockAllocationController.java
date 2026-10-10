package com.renault.fleet.inventory.controller;

import com.renault.fleet.common.api.PageResponse;
import com.renault.fleet.fleetrequest.dto.FleetRequestDetailDto;
import com.renault.fleet.inventory.dto.InventoryStockDto;
import com.renault.fleet.inventory.dto.MapStockRequest;
import com.renault.fleet.inventory.service.InventoryService;
import com.renault.fleet.inventory.service.StockAllocationService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/fleet-requests/{id}")
@Tag(name = "Marketing: stock mapping")
public class StockAllocationController {

    private final InventoryService inventory;
    private final StockAllocationService allocations;

    public StockAllocationController(InventoryService inventory, StockAllocationService allocations) {
        this.inventory = inventory;
        this.allocations = allocations;
    }

    @GetMapping("/stock-options")
    @PreAuthorize("hasAuthority('FLEET_REQUEST_READ')")
    @Operation(summary = "Stock eligible for this request (same variant, active location, units available)")
    public PageResponse<InventoryStockDto> stockOptions(@PathVariable Long id,
            @ParameterObject @PageableDefault(size = 20, sort = "quantityAvailable", direction = Sort.Direction.DESC) Pageable pageable) {
        return inventory.stockOptions(id, pageable);
    }

    @PutMapping("/stock-allocations")
    @PreAuthorize("hasAuthority('STOCK_MAP')")
    @Operation(summary = "Map stock to the request (replaces current allocations; recomputes production required)",
            description = "Locks the request and every touched stock row (SELECT ... FOR UPDATE). An empty `lines` list "
                    + "records that no stock is used. Allowed only in PENDING_MARKETING_REVIEW.")
    public FleetRequestDetailDto mapStock(@PathVariable Long id, @Valid @RequestBody MapStockRequest body,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal) {
        return allocations.mapStock(id, body, principal);
    }
}
