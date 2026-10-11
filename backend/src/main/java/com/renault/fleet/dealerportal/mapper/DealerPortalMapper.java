package com.renault.fleet.dealerportal.mapper;

import com.renault.fleet.dealerportal.dto.DealerPortalDtos;
import com.renault.fleet.dealerportal.entity.DealerOrder;
import com.renault.fleet.dealerportal.entity.DealerRequestStatus;
import com.renault.fleet.fleetrequest.entity.FleetRequest;
import com.renault.fleet.fleetrequest.entity.FleetRequestStatus;
import com.renault.fleet.payment.entity.PaymentReceipt;
import com.renault.fleet.quotation.entity.Quotation;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class DealerPortalMapper {

    /** Internal states in which a dealer may upload proof of payment: a quotation is issued and payment is open. */
    public static final Set<FleetRequestStatus> PAYMENT_PROOF_STATES =
            EnumSet.of(FleetRequestStatus.PAYMENT_PENDING, FleetRequestStatus.FUNDS_VERIFIED);

    public DealerPortalDtos.Request toRequest(FleetRequest r) {
        DealerOrder order = r.getDealerOrder();
        return new DealerPortalDtos.Request(r.getId(), r.getRequestNumber(),
                order == null ? null : order.getId(), order == null ? null : order.getOrderNumber(),
                r.getDealerOrderLineNo(), r.getVariant().getModel().getName(), r.getVariant().getCode(),
                r.getVariant().getName(), r.getColour(), r.getQuantity(), DealerRequestStatus.of(r.getStatus()),
                r.getRequestedDeliveryDate(), r.getCreatedAt(),
                r.getStatus() == FleetRequestStatus.PENDING_MARKETING_REVIEW,
                PAYMENT_PROOF_STATES.contains(r.getStatus()), r.getVersion());
    }

    public DealerPortalDtos.OrderSummary toSummary(DealerOrder o, List<FleetRequest> lines) {
        return new DealerPortalDtos.OrderSummary(o.getId(), o.getOrderNumber(), orderStatus(lines), lines.size(),
                lines.stream().mapToInt(FleetRequest::getQuantity).sum(), o.getRequestedDeliveryDate(), o.getCreatedAt());
    }

    public DealerPortalDtos.OrderDetail toDetail(DealerOrder o, List<FleetRequest> lines) {
        return new DealerPortalDtos.OrderDetail(o.getId(), o.getOrderNumber(), orderStatus(lines),
                o.getRequestedDeliveryDate(), o.getNotes(), o.getCreatedAt(), lines.stream().map(this::toRequest).toList());
    }

    public DealerPortalDtos.Quotation toQuotation(Quotation q) {
        return new DealerPortalDtos.Quotation(q.getId(), q.getQuotationNumber(), q.getIssueDate(), q.getValidUntil(),
                q.getBaseAmount(), q.getGstRate(), q.getGstAmount(), q.getTotalAmount(), q.getRequiredDeposit(),
                q.getDepositReceived(), q.getDepositShortfall(), q.getPaymentStatus());
    }

    public DealerPortalDtos.Receipt toReceipt(PaymentReceipt p, boolean hasProof) {
        return new DealerPortalDtos.Receipt(p.getId(), p.getReceiptNumber(), p.getReceiptType(), p.getPaymentMethod(),
                p.getTransactionReference(), p.getAmount(), p.getReceivedDate(), p.getStatus(), hasProof,
                p.getCreatedAt());
    }

    /** The least advanced open line; CANCELLED only when every line is cancelled. */
    static DealerRequestStatus orderStatus(List<FleetRequest> lines) {
        return lines.stream()
                .map(r -> DealerRequestStatus.of(r.getStatus()))
                .filter(s -> s != DealerRequestStatus.CANCELLED)
                .min(Comparator.naturalOrder())
                .orElse(DealerRequestStatus.CANCELLED);
    }
}
