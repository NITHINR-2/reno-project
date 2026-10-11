package com.renault.fleet.quotation.pdf;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Everything a quotation PDF shows. For an issued quotation it is frozen as JSON on issue and every later download is
 * rendered from it, so the figures can never drift. {@code draft} documents carry no verification URL.
 *
 * @param documentDate the instant stamped into the PDF metadata (issue instant, or creation for drafts)
 */
public record QuotationSnapshot(
        int schemaVersion,
        String quotationNumber,
        String requestNumber,
        boolean draft,
        LocalDate issueDate,
        LocalDate validUntil,
        int validityDays,
        String issuedBy,
        Instant documentDate,
        DealerDetails dealer,
        List<Line> lines,
        BigDecimal gstRate,
        BigDecimal baseAmount,
        BigDecimal gstAmount,
        BigDecimal totalAmount,
        BigDecimal requiredDeposit,
        BigDecimal depositReceived,
        BigDecimal depositShortfall,
        String currency,
        String termsText,
        String verificationUrl) {

    public static final int SCHEMA_VERSION = 1;

    public record DealerDetails(String name, String dealerCode, String gstin, String addressLine, String city,
            String state, String postalCode, String region, String contactName, String contactPhone,
            String contactEmail) {
    }

    public record Line(int lineNo, String description, int quantity, BigDecimal unitPrice, BigDecimal baseAmount,
            BigDecimal gstRate, BigDecimal gstAmount, BigDecimal lineTotal) {
    }
}
