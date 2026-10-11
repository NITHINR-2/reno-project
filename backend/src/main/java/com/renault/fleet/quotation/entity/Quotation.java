package com.renault.fleet.quotation.entity;

import com.renault.fleet.common.persistence.BaseEntity;
import com.renault.fleet.fleetrequest.entity.FleetRequest;
import com.renault.fleet.user.entity.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** GST quotation for a fleet request. Amounts are computed by the backend only (HALF_UP per line, then summed). */
@Entity
@Table(name = "quotations")
public class Quotation extends BaseEntity {

    @Column(nullable = false, length = 30, unique = true)
    private String quotationNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fleet_request_id", nullable = false)
    private FleetRequest fleetRequest;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuotationStatus status = QuotationStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuotationPaymentStatus paymentStatus = QuotationPaymentStatus.UNPAID;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tax_rate_id", nullable = false)
    private TaxRate taxRate;

    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal gstRate;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal baseAmount;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal gstAmount;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal requiredDeposit = BigDecimal.ZERO;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal depositReceived = BigDecimal.ZERO;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal depositShortfall = BigDecimal.ZERO;

    @Column(nullable = false, length = 3)
    private String currency = "INR";

    private LocalDate issueDate;

    private LocalDate validUntil;

    private Instant issuedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issued_by_id")
    private User issuedBy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_id", nullable = false)
    private User createdBy;

    @Column(columnDefinition = "text")
    private String termsText;

    /** Random, unguessable token behind the QR code; set on issue. */
    @Column(length = 64, unique = true)
    private String verificationToken;

    /** Immutable JSON of everything the issued PDF shows (DB trigger refuses changes once written). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String snapshotJson;

    /** SHA-256 (hex) of the PDF rendered from {@link #snapshotJson}. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 64)
    private String pdfSha256;

    @OneToMany(mappedBy = "quotation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo")
    private List<QuotationLineItem> lineItems = new ArrayList<>();

    protected Quotation() {
    }

    public Quotation(String quotationNumber, FleetRequest fleetRequest, TaxRate taxRate, User createdBy) {
        this.quotationNumber = quotationNumber;
        this.fleetRequest = fleetRequest;
        this.taxRate = taxRate;
        this.gstRate = taxRate.getRate();
        this.createdBy = createdBy;
    }

    public void addLineItem(QuotationLineItem item) {
        item.setQuotation(this);
        lineItems.add(item);
    }

    public String getQuotationNumber() { return quotationNumber; }
    public FleetRequest getFleetRequest() { return fleetRequest; }
    public QuotationStatus getStatus() { return status; }
    public void setStatus(QuotationStatus status) { this.status = status; }
    public QuotationPaymentStatus getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(QuotationPaymentStatus paymentStatus) { this.paymentStatus = paymentStatus; }
    public TaxRate getTaxRate() { return taxRate; }
    public BigDecimal getGstRate() { return gstRate; }
    public BigDecimal getBaseAmount() { return baseAmount; }
    public void setBaseAmount(BigDecimal baseAmount) { this.baseAmount = baseAmount; }
    public BigDecimal getGstAmount() { return gstAmount; }
    public void setGstAmount(BigDecimal gstAmount) { this.gstAmount = gstAmount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public BigDecimal getRequiredDeposit() { return requiredDeposit; }
    public void setRequiredDeposit(BigDecimal requiredDeposit) { this.requiredDeposit = requiredDeposit; }
    public BigDecimal getDepositReceived() { return depositReceived; }
    public void setDepositReceived(BigDecimal depositReceived) { this.depositReceived = depositReceived; }
    public BigDecimal getDepositShortfall() { return depositShortfall; }
    public void setDepositShortfall(BigDecimal depositShortfall) { this.depositShortfall = depositShortfall; }
    public String getCurrency() { return currency; }
    public LocalDate getIssueDate() { return issueDate; }
    public void setIssueDate(LocalDate issueDate) { this.issueDate = issueDate; }
    public LocalDate getValidUntil() { return validUntil; }
    public void setValidUntil(LocalDate validUntil) { this.validUntil = validUntil; }
    public Instant getIssuedAt() { return issuedAt; }
    public void setIssuedAt(Instant issuedAt) { this.issuedAt = issuedAt; }
    public User getIssuedBy() { return issuedBy; }
    public void setIssuedBy(User issuedBy) { this.issuedBy = issuedBy; }
    public User getCreatedBy() { return createdBy; }
    public String getTermsText() { return termsText; }
    public void setTermsText(String termsText) { this.termsText = termsText; }
    public List<QuotationLineItem> getLineItems() { return lineItems; }
    public String getVerificationToken() { return verificationToken; }
    public void setVerificationToken(String verificationToken) { this.verificationToken = verificationToken; }
    public String getSnapshotJson() { return snapshotJson; }
    public String getPdfSha256() { return pdfSha256; }

    /** Freezes the issued document; the snapshot and its PDF hash are written once, together. */
    public void freezeDocument(String snapshotJson, String pdfSha256) {
        if (this.snapshotJson != null) {
            throw new IllegalStateException("Quotation " + quotationNumber + " document is already frozen.");
        }
        this.snapshotJson = snapshotJson;
        this.pdfSha256 = pdfSha256;
    }
}
