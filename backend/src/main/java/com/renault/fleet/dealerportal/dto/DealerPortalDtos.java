package com.renault.fleet.dealerportal.dto;

import com.renault.fleet.dealerportal.entity.DealerRequestStatus;
import com.renault.fleet.payment.entity.PaymentMethod;
import com.renault.fleet.payment.entity.ReceiptStatus;
import com.renault.fleet.payment.entity.ReceiptType;
import com.renault.fleet.quotation.entity.QuotationPaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Response shapes of the Dealer Portal. They carry only what a dealer may see: no internal status, notes, return
 * reasons, assignees, stock mapping or SLA data.
 */
public final class DealerPortalDtos {

    private DealerPortalDtos() {
    }

    public record OrderSummary(Long id, String orderNumber, DealerRequestStatus status, int lineCount,
            int totalQuantity, LocalDate requestedDeliveryDate, Instant createdAt) {
    }

    public record OrderDetail(Long id, String orderNumber, DealerRequestStatus status, LocalDate requestedDeliveryDate,
            String notes, Instant createdAt, List<Request> lines) {
    }

    /** One fleet request (an order line, or a request HQ raised for this dealer). */
    public record Request(Long id, String requestNumber, Long orderId, String orderNumber, Integer lineNo,
            String modelName, String variantCode, String variantName, String colour, int quantity,
            DealerRequestStatus status,
            LocalDate requestedDeliveryDate, Instant createdAt, boolean canCancel, boolean canUploadPaymentProof,
            Long version) {
    }

    public record RequestDetail(Request request, Quotation quotation, List<Receipt> receipts) {
    }

    /** The current ISSUED quotation (drafts are never shown to dealers). */
    public record Quotation(Long id, String quotationNumber, LocalDate issueDate, LocalDate validUntil,
            BigDecimal baseAmount, BigDecimal gstRate, BigDecimal gstAmount, BigDecimal totalAmount,
            BigDecimal requiredDeposit, BigDecimal depositReceived, BigDecimal depositShortfall,
            QuotationPaymentStatus paymentStatus) {
    }

    public record Receipt(Long id, String receiptNumber, ReceiptType receiptType, PaymentMethod paymentMethod,
            String transactionReference, BigDecimal amount, LocalDate receivedDate, ReceiptStatus status,
            boolean hasProof, Instant createdAt) {
    }
}
