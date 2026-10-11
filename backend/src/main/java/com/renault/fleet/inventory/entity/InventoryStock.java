package com.renault.fleet.inventory.entity;

import com.renault.fleet.catalog.entity.VehicleColour;
import com.renault.fleet.catalog.entity.VehicleVariant;
import com.renault.fleet.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Stock of one variant (in one colour, or none) at one location: {@code onHand} vehicles physically there, of which
 * {@code reserved} are held for fleet requests. Reserving locks this row (PESSIMISTIC_WRITE) and the database
 * {@code CHECK (reserved >= 0 AND reserved <= on_hand)} is the last line of defence against over-reservation.
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "colour_id")
    private VehicleColour colour;

    @Column(nullable = false)
    private int onHand;

    @Column(nullable = false)
    private int reserved;

    /**
     * The database's generated {@code available} column, for queries, filters and sorting only. Java code reads
     * {@link #getQuantityAvailable()}, which is always current even before a flush.
     */
    @Column(name = "available", insertable = false, updatable = false)
    private Integer quantityAvailable;

    protected InventoryStock() {
    }

    public InventoryStock(InventoryLocation location, VehicleVariant variant, int onHand) {
        this(location, variant, null, onHand);
    }

    public InventoryStock(InventoryLocation location, VehicleVariant variant, VehicleColour colour, int onHand) {
        this.location = location;
        this.variant = variant;
        this.colour = colour;
        this.onHand = onHand;
    }

    /** Holds {@code quantity} more units; the caller has locked the row and checked availability. */
    public void reserve(int quantity) {
        if (quantity < 0 || quantity > getQuantityAvailable()) {
            throw new IllegalStateException("Cannot reserve " + quantity + " of " + getQuantityAvailable() + " available.");
        }
        reserved += quantity;
    }

    /** Gives reserved units back to the free pool (cancellation, rejection, re-mapping, a move to another plant). */
    public void release(int quantity) {
        if (quantity < 0 || quantity > reserved) {
            throw new IllegalStateException("Cannot release " + quantity + " of " + reserved + " reserved.");
        }
        reserved -= quantity;
    }

    /** Reserved units leave with a dispatch: both on-hand and reserved drop. */
    public void consume(int quantity) {
        release(quantity);
        onHand -= quantity;
    }

    public InventoryLocation getLocation() { return location; }
    public VehicleVariant getVariant() { return variant; }
    public VehicleColour getColour() { return colour; }
    public int getOnHand() { return onHand; }
    public void setOnHand(int onHand) { this.onHand = onHand; }
    public int getReserved() { return reserved; }
    /** Free units: on hand minus reserved. */
    public int getQuantityAvailable() { return onHand - reserved; }
    /** Reserved units (the name the API has used since Phase 2). */
    public int getQuantityAllocated() { return reserved; }
}
