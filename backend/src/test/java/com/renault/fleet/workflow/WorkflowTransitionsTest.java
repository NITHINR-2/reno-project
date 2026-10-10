package com.renault.fleet.workflow;

import static com.renault.fleet.fleetrequest.entity.FleetRequestStatus.*;
import static com.renault.fleet.user.entity.RoleCode.ADMIN;
import static com.renault.fleet.user.entity.RoleCode.FINANCE_TEAM;
import static com.renault.fleet.user.entity.RoleCode.MARKETING_HEAD;
import static com.renault.fleet.user.entity.RoleCode.MARKETING_MANAGER;
import static com.renault.fleet.user.entity.RoleCode.SUPERVISOR;
import static org.assertj.core.api.Assertions.assertThat;

import com.renault.fleet.fleetrequest.entity.FleetRequest;
import com.renault.fleet.fleetrequest.entity.FleetRequestStatus;
import com.renault.fleet.user.entity.RoleCode;
import com.renault.fleet.workflow.entity.WorkflowAction;
import com.renault.fleet.workflow.service.WorkflowTransitions;
import com.renault.fleet.workflow.service.WorkflowTransitions.Resolution;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Checks every (status, action, role) combination against an expected table transcribed independently from the
 * CLAUDE.md state machine: 14 statuses x 14 actions x 5 roles.
 */
class WorkflowTransitionsTest {

    private record Row(FleetRequestStatus from, WorkflowAction action, FleetRequestStatus to, Set<RoleCode> roles) {
    }

    private static final List<Row> EXPECTED = expected();

    private static List<Row> expected() {
        List<Row> rows = new ArrayList<>(List.of(
                new Row(PENDING_MARKETING_REVIEW, WorkflowAction.QUALIFY, MARKETING_QUALIFIED, Set.of(MARKETING_MANAGER, SUPERVISOR)),
                new Row(MARKETING_QUALIFIED, WorkflowAction.FORWARD_TO_EXECUTIVE, PENDING_EXECUTIVE_APPROVAL, Set.of(MARKETING_MANAGER, SUPERVISOR)),
                new Row(PENDING_EXECUTIVE_APPROVAL, WorkflowAction.APPROVE, EXECUTIVE_APPROVED, Set.of(MARKETING_HEAD, SUPERVISOR)),
                new Row(PENDING_EXECUTIVE_APPROVAL, WorkflowAction.REJECT, RETURNED_TO_MARKETING, Set.of(MARKETING_HEAD, SUPERVISOR)),
                new Row(RETURNED_TO_MARKETING, WorkflowAction.RESUBMIT, PENDING_MARKETING_REVIEW, Set.of(MARKETING_MANAGER, SUPERVISOR)),
                new Row(EXECUTIVE_APPROVED, WorkflowAction.FORWARD_TO_FINANCE, PENDING_FINANCE_CLEARANCE, Set.of(MARKETING_HEAD, SUPERVISOR)),
                new Row(PENDING_FINANCE_CLEARANCE, WorkflowAction.ISSUE_QUOTATION, QUOTATION_ISSUED, Set.of(FINANCE_TEAM, SUPERVISOR)),
                new Row(PAYMENT_PENDING, WorkflowAction.VERIFY_FUNDS, FUNDS_VERIFIED, Set.of(FINANCE_TEAM, SUPERVISOR)),
                new Row(FUNDS_VERIFIED, WorkflowAction.GRANT_CLEARANCE, FINANCIALLY_CLEARED, Set.of(FINANCE_TEAM, SUPERVISOR)),
                new Row(FINANCIALLY_CLEARED, WorkflowAction.SCHEDULE_PRODUCTION, PRODUCTION_SCHEDULED, Set.of(SUPERVISOR)),
                new Row(FINANCIALLY_CLEARED, WorkflowAction.SCHEDULE_DISPATCH, DISPATCH_SCHEDULED, Set.of(SUPERVISOR)),
                new Row(PRODUCTION_SCHEDULED, WorkflowAction.SCHEDULE_DISPATCH, DISPATCH_SCHEDULED, Set.of(SUPERVISOR)),
                new Row(DISPATCH_SCHEDULED, WorkflowAction.COMPLETE, COMPLETED, Set.of(SUPERVISOR))));
        for (FleetRequestStatus s : FleetRequestStatus.values()) {
            if (s != COMPLETED && s != CANCELLED) {
                rows.add(new Row(s, WorkflowAction.CANCEL, CANCELLED, Set.of(SUPERVISOR)));
            }
        }
        return rows;
    }

    static Stream<Arguments> everyCombination() {
        return Arrays.stream(FleetRequestStatus.values()).flatMap(status ->
                Arrays.stream(WorkflowAction.values()).flatMap(action ->
                        Arrays.stream(RoleCode.values()).map(role -> Arguments.of(status, action, role))));
    }

    @ParameterizedTest(name = "{0} --{1}--> as {2}")
    @MethodSource("everyCombination")
    void everyStatusActionRoleCombinationResolvesAsSpecified(FleetRequestStatus status, WorkflowAction action, RoleCode role) {
        boolean roleMayEverAct = EXPECTED.stream().anyMatch(r -> r.action() == action && r.roles().contains(role));
        Optional<Row> valid = EXPECTED.stream()
                .filter(r -> r.from() == status && r.action() == action && r.roles().contains(role))
                .findFirst();

        Resolution resolution = WorkflowTransitions.resolve(status, action, role);

        if (!roleMayEverAct) {
            assertThat(resolution).as("403 expected").isInstanceOf(Resolution.Forbidden.class);
        } else if (valid.isPresent()) {
            assertThat(resolution).isInstanceOf(Resolution.Allowed.class);
            assertThat(((Resolution.Allowed) resolution).transition().to()).isEqualTo(valid.get().to());
        } else {
            assertThat(resolution).as("409 expected").isInstanceOf(Resolution.Invalid.class);
        }
    }

    @Test
    void tableHasExactlyTheSpecifiedRowsPlusTheAutomaticPaymentStep() {
        assertThat(WorkflowTransitions.all()).hasSize(EXPECTED.size() + 1);
        assertThat(WorkflowTransitions.automaticFrom(QUOTATION_ISSUED))
                .hasValueSatisfying(t -> assertThat(t.to()).isEqualTo(PAYMENT_PENDING));
        assertThat(WorkflowTransitions.resolve(QUOTATION_ISSUED, WorkflowAction.AWAIT_PAYMENT, null))
                .isInstanceOf(Resolution.Allowed.class);
        assertThat(WorkflowTransitions.resolve(PAYMENT_PENDING, WorkflowAction.AWAIT_PAYMENT, null))
                .isInstanceOf(Resolution.Invalid.class);
    }

    @Test
    void adminCanNeverActAndTerminalStatesAcceptNothing() {
        for (WorkflowAction action : WorkflowAction.values()) {
            assertThat(WorkflowTransitions.resolve(PENDING_MARKETING_REVIEW, action, ADMIN))
                    .isInstanceOf(Resolution.Forbidden.class);
            for (RoleCode role : EnumSet.complementOf(EnumSet.of(ADMIN))) {
                assertThat(WorkflowTransitions.resolve(COMPLETED, action, role)).isNotInstanceOf(Resolution.Allowed.class);
                assertThat(WorkflowTransitions.resolve(CANCELLED, action, role)).isNotInstanceOf(Resolution.Allowed.class);
            }
        }
    }

    @Test
    void reasonIsRequiredForRejectAndCancelOnly() {
        WorkflowTransitions.all().forEach(t -> assertThat(t.reasonRequired())
                .as(t.action().name())
                .isEqualTo(t.action() == WorkflowAction.REJECT || t.action() == WorkflowAction.CANCEL));
    }

    @Test
    void qualifyRequiresStockMapping() {
        FleetRequest request = new FleetRequest("FLT-2026-0001", null, null, 50, null);
        assertThat(WorkflowTransitions.available(request, MARKETING_MANAGER)).doesNotContain(WorkflowAction.QUALIFY);
        request.markStockMapped(null, Instant.now());
        assertThat(WorkflowTransitions.available(request, MARKETING_MANAGER)).containsExactly(WorkflowAction.QUALIFY);
    }

    @Test
    void productionIsSkippedOnlyWhenNothingNeedsProducing() {
        FleetRequest request = new FleetRequest("FLT-2026-0001", null, null, 50, null);
        request.setStatus(FINANCIALLY_CLEARED);
        request.applyAllocation(30);
        assertThat(WorkflowTransitions.available(request, SUPERVISOR))
                .contains(WorkflowAction.SCHEDULE_PRODUCTION).doesNotContain(WorkflowAction.SCHEDULE_DISPATCH);
        request.applyAllocation(50);
        assertThat(WorkflowTransitions.available(request, SUPERVISOR))
                .contains(WorkflowAction.SCHEDULE_DISPATCH).doesNotContain(WorkflowAction.SCHEDULE_PRODUCTION);
    }
}
