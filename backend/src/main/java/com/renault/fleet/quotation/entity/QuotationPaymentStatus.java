package com.renault.fleet.quotation.entity;

/** Payment lifecycle, kept separate from the document status. Mirrors {@code ck_quotations_payment_status}. */
public enum QuotationPaymentStatus {
    UNPAID,
    PARTIALLY_PAID,
    PAID;

    /** Derived from approved receipts only: nothing approved is UNPAID, at least the total is PAID. */
    public static QuotationPaymentStatus from(java.math.BigDecimal approved, java.math.BigDecimal total) {
        if (approved.signum() == 0) {
            return UNPAID;
        }
        return approved.compareTo(total) >= 0 ? PAID : PARTIALLY_PAID;
    }
}
