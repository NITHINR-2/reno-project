package com.renault.fleet.fleetrequest.entity;

import com.renault.fleet.catalog.entity.VehicleVariant;
import com.renault.fleet.common.persistence.BaseEntity;
import com.renault.fleet.dealer.entity.Dealer;
import com.renault.fleet.production.entity.Plant;
import com.renault.fleet.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

/** A dealer's fleet request. Status changes only through the WorkflowService (later phase). */
@Entity
@Table(name = "fleet_requests")
public class FleetRequest extends BaseEntity {

    @Column(nullable = false, length = 20, unique = true)
    private String requestNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dealer_id", nullable = false)
    private Dealer dealer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_id", nullable = false)
    private VehicleVariant variant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plant_id")
    private Plant plant;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private int allocatedQty;

    @Column(nullable = false)
    private int productionRequired;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal requiredDeposit = BigDecimal.ZERO;

    private Integer productionEstimateWeeks;

    private LocalDate requestedDeliveryDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private FleetRequestStatus status = FleetRequestStatus.PENDING_MARKETING_REVIEW;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(length = 1000)
    private String returnReason;

    @Column(length = 1000)
    private String cancelReason;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_id", nullable = false)
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to_id")
    private User assignedTo;

    protected FleetRequest() {
    }

    public FleetRequest(String requestNumber, Dealer dealer, VehicleVariant variant, int quantity, User createdBy) {
        this.requestNumber = requestNumber;
        this.dealer = dealer;
        this.variant = variant;
        this.quantity = quantity;
        this.createdBy = createdBy;
    }

    public String getRequestNumber() { return requestNumber; }
    public Dealer getDealer() { return dealer; }
    public VehicleVariant getVariant() { return variant; }
    public void setVariant(VehicleVariant variant) { this.variant = variant; }
    public Plant getPlant() { return plant; }
    public void setPlant(Plant plant) { this.plant = plant; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public int getAllocatedQty() { return allocatedQty; }
    public void setAllocatedQty(int allocatedQty) { this.allocatedQty = allocatedQty; }
    public int getProductionRequired() { return productionRequired; }
    public void setProductionRequired(int productionRequired) { this.productionRequired = productionRequired; }
    public BigDecimal getRequiredDeposit() { return requiredDeposit; }
    public void setRequiredDeposit(BigDecimal requiredDeposit) { this.requiredDeposit = requiredDeposit; }
    public Integer getProductionEstimateWeeks() { return productionEstimateWeeks; }
    public void setProductionEstimateWeeks(Integer weeks) { this.productionEstimateWeeks = weeks; }
    public LocalDate getRequestedDeliveryDate() { return requestedDeliveryDate; }
    public void setRequestedDeliveryDate(LocalDate date) { this.requestedDeliveryDate = date; }
    public FleetRequestStatus getStatus() { return status; }
    public void setStatus(FleetRequestStatus status) { this.status = status; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getReturnReason() { return returnReason; }
    public void setReturnReason(String returnReason) { this.returnReason = returnReason; }
    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }
    public User getCreatedBy() { return createdBy; }
    public User getAssignedTo() { return assignedTo; }
    public void setAssignedTo(User assignedTo) { this.assignedTo = assignedTo; }
}
