package com.renault.fleet.inventory.entity;

import com.renault.fleet.common.persistence.BaseEntity;
import com.renault.fleet.fleetrequest.entity.FleetRequest;
import com.renault.fleet.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "stock_allocations")
public class StockAllocation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fleet_request_id", nullable = false)
    private FleetRequest fleetRequest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_id", nullable = false)
    private InventoryStock stock;

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AllocationStatus status = AllocationStatus.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "allocated_by_id", nullable = false)
    private User allocatedBy;

    private Instant releasedAt;

    protected StockAllocation() {
    }

    public StockAllocation(FleetRequest fleetRequest, InventoryStock stock, int quantity, User allocatedBy) {
        this.fleetRequest = fleetRequest;
        this.stock = stock;
        this.quantity = quantity;
        this.allocatedBy = allocatedBy;
    }

    public FleetRequest getFleetRequest() { return fleetRequest; }
    public InventoryStock getStock() { return stock; }
    public int getQuantity() { return quantity; }
    public AllocationStatus getStatus() { return status; }
    public void setStatus(AllocationStatus status) { this.status = status; }
    public User getAllocatedBy() { return allocatedBy; }
    public Instant getReleasedAt() { return releasedAt; }
    public void setReleasedAt(Instant releasedAt) { this.releasedAt = releasedAt; }
}
