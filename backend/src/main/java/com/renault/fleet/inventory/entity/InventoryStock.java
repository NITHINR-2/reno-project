package com.renault.fleet.inventory.entity;

import com.renault.fleet.catalog.entity.VehicleVariant;
import com.renault.fleet.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Stock of one variant at one location. Allocation locks this row (PESSIMISTIC_WRITE) and the database
 * {@code CHECK (quantity_available >= 0)} is the last line of defence against over-allocation.
 */
@Entity
@Table(name = "inventory_stock")
public class InventoryStock extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private InventoryLocation location;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_id", nullable = false)
    private VehicleVariant variant;

    @Column(nullable = false)
    private int quantityAvailable;

    @Column(nullable = false)
    private int quantityAllocated;

    protected InventoryStock() {
    }

    public InventoryStock(InventoryLocation location, VehicleVariant variant, int quantityAvailable) {
        this.location = location;
        this.variant = variant;
        this.quantityAvailable = quantityAvailable;
    }

    public InventoryLocation getLocation() { return location; }
    public VehicleVariant getVariant() { return variant; }
    public int getQuantityAvailable() { return quantityAvailable; }
    public void setQuantityAvailable(int quantityAvailable) { this.quantityAvailable = quantityAvailable; }
    public int getQuantityAllocated() { return quantityAllocated; }
    public void setQuantityAllocated(int quantityAllocated) { this.quantityAllocated = quantityAllocated; }
}
