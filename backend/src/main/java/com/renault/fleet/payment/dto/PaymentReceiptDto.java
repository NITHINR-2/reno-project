package com.renault.fleet.payment.dto;

import com.renault.fleet.fleetrequest.entity.FleetRequestStatus;
import com.renault.fleet.payment.entity.PaymentMethod;
import com.renault.fleet.payment.entity.ReceiptStatus;
import com.renault.fleet.payment.entity.ReceiptType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record PaymentReceiptDto(
        Long id,
        String receiptNumber,
        Long fleetRequestId,
        String requestNumber,
        FleetRequestStatus requestStatus,
        String quotationNumber,
        ReceiptType receiptType,
        PaymentMethod paymentMethod,
        String transactionReference,
        BigDecimal amount,
        LocalDate receivedDate,
        ReceiptStatus status,
        String recordedBy,
        String reviewedBy,
        Instant reviewedAt,
        Long version,
        Instant createdAt) {
}
