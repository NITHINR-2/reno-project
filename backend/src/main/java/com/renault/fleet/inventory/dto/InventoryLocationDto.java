package com.renault.fleet.inventory.dto;

import com.renault.fleet.inventory.entity.LocationType;

public record InventoryLocationDto(
        Long id,
        String code,
        String name,
        LocationType locationType,
        Long regionId,
        String regionName,
        Long plantId,
        String plantName,
        boolean active) {
}
