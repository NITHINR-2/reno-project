package com.renault.fleet.workflow.service;

import com.renault.fleet.payment.entity.ReceiptStatus;
import com.renault.fleet.payment.entity.ReceiptType;
import com.renault.fleet.payment.repository.PaymentReceiptRepository;
import com.renault.fleet.quotation.entity.QuotationStatus;
import com.renault.fleet.quotation.repository.QuotationRepository;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/** Guard facts read from the quotation and payment tables (joins the caller's transaction). */
@Component
public class RepositoryWorkflowFacts implements WorkflowFacts {

    private final QuotationRepository quotations;
    private final PaymentReceiptRepository receipts;

    public RepositoryWorkflowFacts(QuotationRepository quotations, PaymentReceiptRepository receipts) {
        this.quotations = quotations;
        this.receipts = receipts;
    }

    @Override
    public boolean hasIssuedQuotation(Long requestId) {
        return quotations.existsByFleetRequestIdAndStatus(requestId, QuotationStatus.ISSUED);
    }

    @Override
    public BigDecimal eligibleDeposits(Long requestId) {
        return receipts.sumAmount(requestId, ReceiptType.DEPOSIT, ReceiptStatus.APPROVED);
    }
}
