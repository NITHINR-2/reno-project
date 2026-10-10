package com.renault.fleet.payment.entity;

import com.renault.fleet.common.persistence.BaseEntity;
import com.renault.fleet.fleetrequest.entity.FleetRequest;
import com.renault.fleet.quotation.entity.Quotation;
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
import java.time.Instant;
import java.time.LocalDate;

/**
 * A deposit or payment receipt. {@code transactionReference} is unique (case- and whitespace-insensitive index);
 * approval locks the row and is idempotent (a second approval is a 409).
 */
@Entity
@Table(name = "payment_receipts")
public class PaymentReceipt extends BaseEntity {

    @Column(nullable = false, length = 30, unique = true)
    private String receiptNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fleet_request_id", nullable = false)
    private FleetRequest fleetRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quotation_id")
    private Quotation quotation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReceiptType receiptType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod paymentMethod;

    @Column(nullable = false, length = 100)
    private String transactionReference;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDate receivedDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReceiptStatus status = ReceiptStatus.RECORDED;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recorded_by_id", nullable = false)
    private User recordedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_id")
    private User reviewedBy;

    private Instant reviewedAt;

    @Column(length = 1000)
    private String rejectionReason;

    protected PaymentReceipt() {
    }

    public PaymentReceipt(String receiptNumber, FleetRequest fleetRequest, ReceiptType receiptType, PaymentMethod paymentMethod,
            String transactionReference, BigDecimal amount, LocalDate receivedDate, User recordedBy) {
        this.receiptNumber = receiptNumber;
        this.fleetRequest = fleetRequest;
        this.receiptType = receiptType;
        this.paymentMethod = paymentMethod;
        this.transactionReference = transactionReference;
        this.amount = amount;
        this.receivedDate = receivedDate;
        this.recordedBy = recordedBy;
    }

    public String getReceiptNumber() { return receiptNumber; }
    public FleetRequest getFleetRequest() { return fleetRequest; }
    public Quotation getQuotation() { return quotation; }
    public void setQuotation(Quotation quotation) { this.quotation = quotation; }
    public ReceiptType getReceiptType() { return receiptType; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public String getTransactionReference() { return transactionReference; }
    public BigDecimal getAmount() { return amount; }
    public LocalDate getReceivedDate() { return receivedDate; }
    public ReceiptStatus getStatus() { return status; }
    public void setStatus(ReceiptStatus status) { this.status = status; }
    public User getRecordedBy() { return recordedBy; }
    public User getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(User reviewedBy) { this.reviewedBy = reviewedBy; }
    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
}
