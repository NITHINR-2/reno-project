package com.renault.fleet.fleetrequest.entity;

/** Workflow states (see CLAUDE.md state machine). Mirrors {@code ck_fleet_requests_status}. */
public enum FleetRequestStatus {
    PENDING_MARKETING_REVIEW,
    MARKETING_QUALIFIED,
    PENDING_EXECUTIVE_APPROVAL,
    EXECUTIVE_APPROVED,
    RETURNED_TO_MARKETING,
    PENDING_FINANCE_CLEARANCE,
    QUOTATION_ISSUED,
    PAYMENT_PENDING,
    FUNDS_VERIFIED,
    FINANCIALLY_CLEARED,
    PRODUCTION_SCHEDULED,
    DISPATCH_SCHEDULED,
    COMPLETED,
    CANCELLED;

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED;
    }
}
