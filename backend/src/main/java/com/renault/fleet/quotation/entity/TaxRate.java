package com.renault.fleet.quotation.entity;

import com.renault.fleet.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Effective-dated GST rate per vehicle category. {@code effectiveTo} is exclusive and null means open-ended;
 * overlapping periods for the same category are rejected by an EXCLUDE constraint.
 */
@Entity
@Table(name = "tax_rates")
public class TaxRate extends BaseEntity {

    @Column(nullable = false, length = 40)
    private String vehicleCategory;

    @Column(nullable = false, length = 10)
    private String taxType = "GST";

    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal rate;

    @Column(nullable = false)
    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;

    @Column(length = 500)
    private String notes;

    protected TaxRate() {
    }

    public TaxRate(String vehicleCategory, BigDecimal rate, LocalDate effectiveFrom) {
        this.vehicleCategory = vehicleCategory;
        this.rate = rate;
        this.effectiveFrom = effectiveFrom;
    }

    public String getVehicleCategory() { return vehicleCategory; }
    public String getTaxType() { return taxType; }
    public BigDecimal getRate() { return rate; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public LocalDate getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(LocalDate effectiveTo) { this.effectiveTo = effectiveTo; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
