package com.renault.fleet.inventory.dto;

import com.renault.fleet.inventory.entity.LocationType;

/** Query-string filters for {@code GET /api/inventory/stock}; every field is optional. */
public record InventoryStockFilter(
        Long variantId,
        Long modelId,
        Long locationId,
        LocationType locationType,
        Boolean availableOnly,
        Boolean activeLocationsOnly) {
}
