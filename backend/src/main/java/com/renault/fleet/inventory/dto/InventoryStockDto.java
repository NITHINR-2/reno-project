package com.renault.fleet.inventory.dto;

import com.renault.fleet.inventory.entity.LocationType;

public record InventoryStockDto(
        Long id,
        Long locationId,
        String locationCode,
        String locationName,
        LocationType locationType,
        Long variantId,
        String variantCode,
        String variantName,
        int quantityAvailable,
        int quantityAllocated,
        Long version) {
}
