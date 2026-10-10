package com.renault.fleet.payment.mapper;

import com.renault.fleet.payment.dto.PaymentReceiptDto;
import com.renault.fleet.payment.entity.PaymentReceipt;
import com.renault.fleet.user.entity.User;
import org.springframework.stereotype.Component;

@Component
public class PaymentReceiptMapper {

    /** Must run inside a transaction (request, quotation and users are lazy). */
    public PaymentReceiptDto toDto(PaymentReceipt r) {
        return new PaymentReceiptDto(r.getId(), r.getReceiptNumber(), r.getFleetRequest().getId(),
                r.getFleetRequest().getRequestNumber(), r.getFleetRequest().getStatus(),
                r.getQuotation() == null ? null : r.getQuotation().getQuotationNumber(), r.getReceiptType(),
                r.getPaymentMethod(), r.getTransactionReference(), r.getAmount(), r.getReceivedDate(), r.getStatus(),
                name(r.getRecordedBy()), name(r.getReviewedBy()), r.getReviewedAt(), r.getVersion(), r.getCreatedAt());
    }

    private static String name(User user) {
        return user == null ? null : user.getFullName();
    }
}
