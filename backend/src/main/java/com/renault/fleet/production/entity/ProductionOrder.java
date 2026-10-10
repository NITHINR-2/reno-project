package com.renault.fleet.production.entity;

import com.renault.fleet.catalog.entity.VehicleVariant;
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
import java.time.LocalDate;

@Entity
@Table(name = "production_orders")
public class ProductionOrder extends BaseEntity {

    @Column(nullable = false, length = 30, unique = true)
    private String productionNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fleet_request_id", nullable = false)
    private FleetRequest fleetRequest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plant_id", nullable = false)
    private Plant plant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_id", nullable = false)
    private VehicleVariant variant;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private LocalDate scheduledStart;

    @Column(nullable = false)
    private LocalDate estimatedCompletion;

    private Instant completedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductionStatus status = ProductionStatus.SCHEDULED;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scheduled_by_id", nullable = false)
    private User scheduledBy;

    @Column(length = 1000)
    private String notes;

    protected ProductionOrder() {
    }

    public ProductionOrder(String productionNumber, FleetRequest fleetRequest, Plant plant, VehicleVariant variant,
            int quantity, LocalDate scheduledStart, LocalDate estimatedCompletion, User scheduledBy) {
        this.productionNumber = productionNumber;
        this.fleetRequest = fleetRequest;
        this.plant = plant;
        this.variant = variant;
        this.quantity = quantity;
        this.scheduledStart = scheduledStart;
        this.estimatedCompletion = estimatedCompletion;
        this.scheduledBy = scheduledBy;
    }

    public String getProductionNumber() { return productionNumber; }
    public FleetRequest getFleetRequest() { return fleetRequest; }
    public Plant getPlant() { return plant; }
    public VehicleVariant getVariant() { return variant; }
    public int getQuantity() { return quantity; }
    public LocalDate getScheduledStart() { return scheduledStart; }
    public void setScheduledStart(LocalDate scheduledStart) { this.scheduledStart = scheduledStart; }
    public LocalDate getEstimatedCompletion() { return estimatedCompletion; }
    public void setEstimatedCompletion(LocalDate date) { this.estimatedCompletion = date; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public ProductionStatus getStatus() { return status; }
    public void setStatus(ProductionStatus status) { this.status = status; }
    public User getScheduledBy() { return scheduledBy; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
