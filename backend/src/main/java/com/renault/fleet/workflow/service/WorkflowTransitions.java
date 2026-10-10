package com.renault.fleet.workflow.service;

import static com.renault.fleet.fleetrequest.entity.FleetRequestStatus.*;
import static com.renault.fleet.user.entity.RoleCode.FINANCE_TEAM;
import static com.renault.fleet.user.entity.RoleCode.MARKETING_HEAD;
import static com.renault.fleet.user.entity.RoleCode.MARKETING_MANAGER;
import static com.renault.fleet.user.entity.RoleCode.SUPERVISOR;

import com.renault.fleet.fleetrequest.entity.FleetRequest;
import com.renault.fleet.fleetrequest.entity.FleetRequestStatus;
import com.renault.fleet.user.entity.RoleCode;
import com.renault.fleet.workflow.entity.WorkflowAction;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The explicit transition table of the fleet-request state machine (CLAUDE.md). Each row is
 * (from, action, to, allowed roles, guard, reason required). SUPERVISOR is allowed on every human row; an empty
 * role set marks a system-only (automatic) row. Resolution here is pure; guards are evaluated by the service.
 */
public final class WorkflowTransitions {

    /** Returns a failure message when the transition must not happen, or empty when it may. */
    @FunctionalInterface
    public interface Guard {
        Optional<String> check(FleetRequest request, WorkflowFacts facts);

        Guard NONE = (request, facts) -> Optional.empty();
    }

    public record Transition(FleetRequestStatus from, WorkflowAction action, FleetRequestStatus to,
            Set<RoleCode> roles, Guard guard, boolean reasonRequired) {

        public boolean isAutomatic() {
            return roles.isEmpty();
        }
    }

    /** Outcome of looking up (status, action, role) in the table. */
    public sealed interface Resolution {
        record Allowed(Transition transition) implements Resolution {
        }

        /** The role may never perform this action (403). */
        record Forbidden() implements Resolution {
        }

        /** The role may perform the action, but not from this status (409). */
        record Invalid() implements Resolution {
        }
    }

    private static final Guard STOCK_MAPPED = (r, facts) -> r.getStockMappedAt() != null
            ? Optional.empty()
            : Optional.of("Stock must be mapped before the request can be qualified.");
    private static final Guard NEEDS_PRODUCTION = (r, facts) -> r.getProductionRequired() > 0
            ? Optional.empty()
            : Optional.of("No production is required; schedule dispatch instead.");
    private static final Guard NO_PRODUCTION = (r, facts) -> r.getProductionRequired() == 0
            ? Optional.empty()
            : Optional.of("Production of " + r.getProductionRequired() + " units must be scheduled first.");
    private static final Guard QUOTATION_ISSUED_EXISTS = (r, facts) -> facts.hasIssuedQuotation(r.getId())
            ? Optional.empty()
            : Optional.of("An ISSUED quotation is required; issue the draft quotation first.");
    /** Funds are verified when approved deposits cover the required deposit. */
    private static final Guard DEPOSIT_COVERED = (r, facts) -> {
        BigDecimal eligible = facts.eligibleDeposits(r.getId());
        return eligible.compareTo(r.getRequiredDeposit()) >= 0
                ? Optional.empty()
                : Optional.of("Approved deposits of " + eligible.toPlainString() + " do not cover the required deposit of "
                        + r.getRequiredDeposit().toPlainString() + ".");
    };

    private static final List<Transition> TABLE = build();

    private WorkflowTransitions() {
    }

    private static List<Transition> build() {
        List<Transition> rows = new ArrayList<>();
        rows.add(row(PENDING_MARKETING_REVIEW, WorkflowAction.QUALIFY, MARKETING_QUALIFIED, STOCK_MAPPED, false, MARKETING_MANAGER));
        rows.add(row(MARKETING_QUALIFIED, WorkflowAction.FORWARD_TO_EXECUTIVE, PENDING_EXECUTIVE_APPROVAL, Guard.NONE, false, MARKETING_MANAGER));
        rows.add(row(PENDING_EXECUTIVE_APPROVAL, WorkflowAction.APPROVE, EXECUTIVE_APPROVED, Guard.NONE, false, MARKETING_HEAD));
        rows.add(row(PENDING_EXECUTIVE_APPROVAL, WorkflowAction.REJECT, RETURNED_TO_MARKETING, Guard.NONE, true, MARKETING_HEAD));
        rows.add(row(RETURNED_TO_MARKETING, WorkflowAction.RESUBMIT, PENDING_MARKETING_REVIEW, Guard.NONE, false, MARKETING_MANAGER));
        rows.add(row(EXECUTIVE_APPROVED, WorkflowAction.FORWARD_TO_FINANCE, PENDING_FINANCE_CLEARANCE, Guard.NONE, false, MARKETING_HEAD));
        rows.add(row(PENDING_FINANCE_CLEARANCE, WorkflowAction.ISSUE_QUOTATION, QUOTATION_ISSUED, QUOTATION_ISSUED_EXISTS, false, FINANCE_TEAM));
        rows.add(new Transition(QUOTATION_ISSUED, WorkflowAction.AWAIT_PAYMENT, PAYMENT_PENDING, Set.of(), Guard.NONE, false));
        rows.add(row(PAYMENT_PENDING, WorkflowAction.VERIFY_FUNDS, FUNDS_VERIFIED, DEPOSIT_COVERED, false, FINANCE_TEAM));
        rows.add(row(FUNDS_VERIFIED, WorkflowAction.GRANT_CLEARANCE, FINANCIALLY_CLEARED, DEPOSIT_COVERED, false, FINANCE_TEAM));
        rows.add(row(FINANCIALLY_CLEARED, WorkflowAction.SCHEDULE_PRODUCTION, PRODUCTION_SCHEDULED, NEEDS_PRODUCTION, false));
        rows.add(row(FINANCIALLY_CLEARED, WorkflowAction.SCHEDULE_DISPATCH, DISPATCH_SCHEDULED, NO_PRODUCTION, false));
        rows.add(row(PRODUCTION_SCHEDULED, WorkflowAction.SCHEDULE_DISPATCH, DISPATCH_SCHEDULED, Guard.NONE, false));
        rows.add(row(DISPATCH_SCHEDULED, WorkflowAction.COMPLETE, COMPLETED, Guard.NONE, false));
        Arrays.stream(FleetRequestStatus.values())
                .filter(status -> !status.isTerminal())
                .forEach(status -> rows.add(row(status, WorkflowAction.CANCEL, CANCELLED, Guard.NONE, true)));
        return List.copyOf(rows);
    }

    /** A human row: SUPERVISOR is always allowed in addition to {@code roles}. */
    private static Transition row(FleetRequestStatus from, WorkflowAction action, FleetRequestStatus to, Guard guard,
            boolean reasonRequired, RoleCode... roles) {
        EnumSet<RoleCode> allowed = EnumSet.of(SUPERVISOR, roles);
        return new Transition(from, action, to, Set.copyOf(allowed), guard, reasonRequired);
    }

    public static List<Transition> all() {
        return TABLE;
    }

    /** Roles that may ever perform {@code action} (empty for system-only actions). */
    public static Set<RoleCode> rolesFor(WorkflowAction action) {
        EnumSet<RoleCode> roles = EnumSet.noneOf(RoleCode.class);
        TABLE.stream().filter(t -> t.action() == action).forEach(t -> roles.addAll(t.roles()));
        return roles;
    }

    /**
     * Looks up a transition. {@code role == null} means the system (automatic rows only). A role check comes first,
     * so a role that may never take the action gets Forbidden regardless of status.
     */
    public static Resolution resolve(FleetRequestStatus from, WorkflowAction action, RoleCode role) {
        if (role != null && !rolesFor(action).contains(role)) {
            return new Resolution.Forbidden();
        }
        return TABLE.stream()
                .filter(t -> t.from() == from && t.action() == action)
                .filter(t -> role == null ? t.isAutomatic() : t.roles().contains(role))
                .findFirst()
                .<Resolution>map(Resolution.Allowed::new)
                .orElseGet(Resolution.Invalid::new);
    }

    /** Actions {@code role} could take on {@code request} right now (state, role and guard all pass). */
    public static List<WorkflowAction> available(FleetRequest request, RoleCode role, WorkflowFacts facts) {
        return TABLE.stream()
                .filter(t -> t.from() == request.getStatus() && t.roles().contains(role))
                .filter(t -> t.guard().check(request, facts).isEmpty())
                .map(Transition::action)
                .distinct()
                .toList();
    }

    /** The automatic row leaving {@code status}, if any (e.g. QUOTATION_ISSUED → PAYMENT_PENDING). */
    public static Optional<Transition> automaticFrom(FleetRequestStatus status) {
        return TABLE.stream().filter(t -> t.from() == status && t.isAutomatic()).findFirst();
    }
}
