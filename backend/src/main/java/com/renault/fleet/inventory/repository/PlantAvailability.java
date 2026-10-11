package com.renault.fleet.inventory.repository;

/** One row of {@link InventoryStockRepository#availabilityAtPlant}; {@code colourId} is null for uncoloured stock. */
public interface PlantAvailability {
    Long getVariantId();

    Long getColourId();

    Long getAvailable();
}
