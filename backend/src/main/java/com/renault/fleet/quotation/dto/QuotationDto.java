package com.renault.fleet.quotation.dto;

import com.renault.fleet.fleetrequest.entity.FleetRequestStatus;
import com.renault.fleet.quotation.entity.QuotationPaymentStatus;
import com.renault.fleet.quotation.entity.QuotationStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * A quotation with its full BigDecimal breakdown. {@code status} is the document state; {@code paymentStatus} is
 * separate and only ever moves when receipts are approved (issuing is never payment).
 */
public record QuotationDto(
        Long id,
        String quotationNumber,
        Long fleetRequestId,
        String requestNumber,
        FleetRequestStatus requestStatus,
        QuotationStatus status,
        QuotationPaymentStatus paymentStatus,
        String currency,
        BigDecimal gstRate,
        BigDecimal baseAmount,
        BigDecimal gstAmount,
        BigDecimal totalAmount,
        BigDecimal requiredDeposit,
        BigDecimal depositReceived,
        BigDecimal depositShortfall,
        List<Line> lines,
        LocalDate issueDate,
        LocalDate validUntil,
        Instant issuedAt,
        String issuedBy,
        String createdBy,
        String termsText,
        Long version,
        Instant createdAt) {

    public record Line(int lineNo, Long variantId, String description, int quantity, BigDecimal unitPrice,
            BigDecimal baseAmount, BigDecimal gstRate, BigDecimal gstAmount, BigDecimal lineTotal) {
    }
}
