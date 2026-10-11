package com.renault.fleet.quotation.mapper;

import com.renault.fleet.quotation.dto.QuotationDto;
import com.renault.fleet.quotation.entity.Quotation;
import com.renault.fleet.quotation.entity.QuotationLineItem;
import com.renault.fleet.user.entity.User;
import org.springframework.stereotype.Component;

@Component
public class QuotationMapper {

    /** Must run inside a transaction (fleet request, users and line items are lazy). */
    public QuotationDto toDto(Quotation q) {
        return new QuotationDto(q.getId(), q.getQuotationNumber(), q.getFleetRequest().getId(),
                q.getFleetRequest().getRequestNumber(), q.getFleetRequest().getStatus(), q.getStatus(),
                q.getPaymentStatus(), q.getCurrency(), q.getGstRate(), q.getBaseAmount(), q.getGstAmount(),
                q.getTotalAmount(), q.getRequiredDeposit(), q.getDepositReceived(), q.getDepositShortfall(),
                q.getLineItems().stream().map(QuotationMapper::toLine).toList(), q.getIssueDate(), q.getValidUntil(),
                q.getIssuedAt(), name(q.getIssuedBy()), name(q.getCreatedBy()), q.getTermsText(), q.getPdfSha256(), q.getVersion(),
                q.getCreatedAt());
    }

    private static QuotationDto.Line toLine(QuotationLineItem l) {
        return new QuotationDto.Line(l.getLineNo(), l.getVariant().getId(), l.getDescription(), l.getQuantity(),
                l.getUnitPrice(), l.getBaseAmount(), l.getGstRate(), l.getGstAmount(), l.getLineTotal());
    }

    private static String name(User user) {
        return user == null ? null : user.getFullName();
    }
}
