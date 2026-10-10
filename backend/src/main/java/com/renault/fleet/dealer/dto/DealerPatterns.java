package com.renault.fleet.dealer.dto;

/** Validation patterns shared by the dealer DTOs. GSTIN mirrors {@code ck_dealers_gstin}. */
final class DealerPatterns {

    static final String CODE = "^[A-Z0-9][A-Z0-9-]*$";
    static final String CODE_MESSAGE = "must be upper-case letters, digits and hyphens";
    static final String GSTIN = "^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$";
    static final String GSTIN_MESSAGE = "must be a valid 15-character GSTIN";
    static final String PHONE = "^\\+?[0-9 -]{7,20}$";
    static final String PHONE_MESSAGE = "must be a valid phone number";
    static final String PIN = "^[1-9][0-9]{5}$";
    static final String PIN_MESSAGE = "must be a 6-digit PIN code";

    private DealerPatterns() {
    }
}
