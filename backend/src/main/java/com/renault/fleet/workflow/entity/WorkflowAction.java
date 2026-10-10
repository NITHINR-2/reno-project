package com.renault.fleet.workflow.entity;

/** Actions of the fleet-request state machine (see CLAUDE.md and {@code WorkflowTransitions}). */
public enum WorkflowAction {
    QUALIFY,
    FORWARD_TO_EXECUTIVE,
    APPROVE,
    REJECT,
    RESUBMIT,
    FORWARD_TO_FINANCE,
    ISSUE_QUOTATION,
    /** System-only: QUOTATION_ISSUED moves to PAYMENT_PENDING automatically. */
    AWAIT_PAYMENT,
    VERIFY_FUNDS,
    GRANT_CLEARANCE,
    SCHEDULE_PRODUCTION,
    SCHEDULE_DISPATCH,
    COMPLETE,
    CANCEL
}
