package com.renault.fleet.inventory.dto;

import com.renault.fleet.inventory.entity.LocationType;

/** Query-string filters for {@code GET /api/inventory/locations}; every field is optional. */
public record InventoryLocationFilter(LocationType type, Long regionId, Long plantId, Boolean active) {
}
