package com.renault.fleet.dispatch.entity;

import com.renault.fleet.common.persistence.BaseEntity;
import com.renault.fleet.fleetrequest.entity.FleetRequest;
import com.renault.fleet.inventory.entity.InventoryLocation;
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
import java.time.LocalDate;

@Entity
@Table(name = "dispatches")
public class Dispatch extends BaseEntity {

    @Column(nullable = false, length = 30, unique = true)
    private String dispatchNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fleet_request_id", nullable = false)
    private FleetRequest fleetRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_location_id")
    private InventoryLocation fromLocation;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private LocalDate scheduledDate;

    private Instant dispatchedAt;

    private Instant deliveredAt;

    @Column(length = 150)
    private String carrier;

    @Column(length = 100)
    private String vehicleReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DispatchStatus status = DispatchStatus.SCHEDULED;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scheduled_by_id", nullable = false)
    private User scheduledBy;

    @Column(length = 1000)
    private String notes;

    protected Dispatch() {
    }

    public Dispatch(String dispatchNumber, FleetRequest fleetRequest, int quantity, LocalDate scheduledDate, User scheduledBy) {
        this.dispatchNumber = dispatchNumber;
        this.fleetRequest = fleetRequest;
        this.quantity = quantity;
        this.scheduledDate = scheduledDate;
        this.scheduledBy = scheduledBy;
    }

    public String getDispatchNumber() { return dispatchNumber; }
    public FleetRequest getFleetRequest() { return fleetRequest; }
    public InventoryLocation getFromLocation() { return fromLocation; }
    public void setFromLocation(InventoryLocation fromLocation) { this.fromLocation = fromLocation; }
    public int getQuantity() { return quantity; }
    public LocalDate getScheduledDate() { return scheduledDate; }
    public void setScheduledDate(LocalDate scheduledDate) { this.scheduledDate = scheduledDate; }
    public Instant getDispatchedAt() { return dispatchedAt; }
    public void setDispatchedAt(Instant dispatchedAt) { this.dispatchedAt = dispatchedAt; }
    public Instant getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(Instant deliveredAt) { this.deliveredAt = deliveredAt; }
    public String getCarrier() { return carrier; }
    public void setCarrier(String carrier) { this.carrier = carrier; }
    public String getVehicleReference() { return vehicleReference; }
    public void setVehicleReference(String vehicleReference) { this.vehicleReference = vehicleReference; }
    public DispatchStatus getStatus() { return status; }
    public void setStatus(DispatchStatus status) { this.status = status; }
    public User getScheduledBy() { return scheduledBy; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
