package com.renault.fleet.quotation.entity;

/** Document lifecycle. Mirrors {@code ck_quotations_status}; at most one ISSUED per request (partial unique index). */
public enum QuotationStatus {
    DRAFT,
    ISSUED,
    SUPERSEDED,
    CANCELLED
}
