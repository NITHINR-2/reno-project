package com.renault.fleet.inventory.mapper;

import com.renault.fleet.dealer.entity.Region;
import com.renault.fleet.inventory.dto.InventoryLocationDto;
import com.renault.fleet.inventory.dto.InventoryStockDto;
import com.renault.fleet.inventory.dto.StockAllocationDto;
import com.renault.fleet.inventory.entity.InventoryLocation;
import com.renault.fleet.inventory.entity.InventoryStock;
import com.renault.fleet.inventory.entity.StockAllocation;
import com.renault.fleet.production.entity.Plant;
import org.springframework.stereotype.Component;

/** Expects the associations it reads to be fetched (see the repositories' entity graphs). */
@Component
public class InventoryMapper {

    public InventoryStockDto toStock(InventoryStock s) {
        InventoryLocation location = s.getLocation();
        return new InventoryStockDto(s.getId(), location.getId(), location.getCode(), location.getName(),
                location.getLocationType(), s.getVariant().getId(), s.getVariant().getCode(), s.getVariant().getName(),
                s.getQuantityAvailable(), s.getQuantityAllocated(), s.getVersion(), s.getOnHand(),
                s.getColour() == null ? null : s.getColour().getId(), s.getColour() == null ? null : s.getColour().getName());
    }

    public InventoryLocationDto toLocation(InventoryLocation l) {
        Region region = l.getRegion();
        Plant plant = l.getPlant();
        return new InventoryLocationDto(l.getId(), l.getCode(), l.getName(), l.getLocationType(),
                region == null ? null : region.getId(), region == null ? null : region.getName(),
                plant == null ? null : plant.getId(), plant == null ? null : plant.getName(), l.isActive());
    }

    public StockAllocationDto toAllocation(StockAllocation a) {
        InventoryLocation location = a.getStock().getLocation();
        return new StockAllocationDto(a.getId(), a.getStock().getId(), location.getId(), location.getCode(),
                location.getName(), a.getQuantity(), a.getStatus(), a.getAllocatedBy().getFullName(), a.getCreatedAt());
    }
}
