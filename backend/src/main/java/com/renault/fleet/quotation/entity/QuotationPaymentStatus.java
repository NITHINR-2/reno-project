package com.renault.fleet.quotation.entity;

/** Payment lifecycle, kept separate from the document status. Mirrors {@code ck_quotations_payment_status}. */
public enum QuotationPaymentStatus {
    UNPAID,
    PARTIALLY_PAID,
    PAID
}
