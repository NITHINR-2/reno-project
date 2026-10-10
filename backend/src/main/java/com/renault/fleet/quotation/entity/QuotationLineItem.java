package com.renault.fleet.quotation.entity;

import com.renault.fleet.catalog.entity.VehicleVariant;
import com.renault.fleet.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "quotation_line_items")
public class QuotationLineItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quotation_id", nullable = false)
    private Quotation quotation;

    @Column(nullable = false)
    private int lineNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_id", nullable = false)
    private VehicleVariant variant;

    @Column(nullable = false, length = 255)
    private String description;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal baseAmount;

    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal gstRate;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal gstAmount;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal lineTotal;

    protected QuotationLineItem() {
    }

    public QuotationLineItem(int lineNo, VehicleVariant variant, String description, int quantity, BigDecimal unitPrice,
            BigDecimal baseAmount, BigDecimal gstRate, BigDecimal gstAmount, BigDecimal lineTotal) {
        this.lineNo = lineNo;
        this.variant = variant;
        this.description = description;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.baseAmount = baseAmount;
        this.gstRate = gstRate;
        this.gstAmount = gstAmount;
        this.lineTotal = lineTotal;
    }

    void setQuotation(Quotation quotation) { this.quotation = quotation; }

    public Quotation getQuotation() { return quotation; }
    public int getLineNo() { return lineNo; }
    public VehicleVariant getVariant() { return variant; }
    public String getDescription() { return description; }
    public int getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getBaseAmount() { return baseAmount; }
    public BigDecimal getGstRate() { return gstRate; }
    public BigDecimal getGstAmount() { return gstAmount; }
    public BigDecimal getLineTotal() { return lineTotal; }
}
