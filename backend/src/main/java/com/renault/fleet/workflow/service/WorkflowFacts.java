package com.renault.fleet.workflow.service;

import java.math.BigDecimal;

/** Data the workflow guards need from other features (quotations, payments), looked up per request. */
public interface WorkflowFacts {

    boolean hasIssuedQuotation(Long requestId);

    /** Approved DEPOSIT receipts for the request (the "eligible deposits" of the quotation formula). */
    BigDecimal eligibleDeposits(Long requestId);
}
