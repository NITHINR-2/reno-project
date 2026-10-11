package com.renault.fleet.dealerportal.entity;

import com.renault.fleet.common.persistence.BaseEntity;
import com.renault.fleet.dealer.entity.Dealer;
import com.renault.fleet.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;

/** A dealer's order from the portal. Each line becomes its own fleet request ({@code fleet_requests.dealer_order_id}). */
@Entity
@Table(name = "dealer_orders")
public class DealerOrder extends BaseEntity {

    @Column(nullable = false, length = 20, unique = true)
    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dealer_id", nullable = false)
    private Dealer dealer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_id", nullable = false)
    private User createdBy;

    private LocalDate requestedDeliveryDate;

    @Column(length = 2000)
    private String notes;

    protected DealerOrder() {
    }

    public DealerOrder(String orderNumber, Dealer dealer, User createdBy, LocalDate requestedDeliveryDate, String notes) {
        this.orderNumber = orderNumber;
        this.dealer = dealer;
        this.createdBy = createdBy;
        this.requestedDeliveryDate = requestedDeliveryDate;
        this.notes = notes;
    }

    public String getOrderNumber() { return orderNumber; }
    public Dealer getDealer() { return dealer; }
    public User getCreatedBy() { return createdBy; }
    public LocalDate getRequestedDeliveryDate() { return requestedDeliveryDate; }
    public String getNotes() { return notes; }
}
