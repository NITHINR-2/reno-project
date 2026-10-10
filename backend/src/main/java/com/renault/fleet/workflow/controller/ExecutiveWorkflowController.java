package com.renault.fleet.workflow.controller;

import com.renault.fleet.fleetrequest.dto.FleetRequestDetailDto;
import com.renault.fleet.fleetrequest.dto.ReasonRequest;
import com.renault.fleet.fleetrequest.dto.TransitionRequest;
import com.renault.fleet.security.AuthenticatedUser;
import com.renault.fleet.workflow.entity.WorkflowAction;
import com.renault.fleet.workflow.service.WorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/fleet-requests/{id}")
@Tag(name = "Executive approval")
public class ExecutiveWorkflowController {

    private final WorkflowService workflow;

    public ExecutiveWorkflowController(WorkflowService workflow) {
        this.workflow = workflow;
    }

    @PostMapping("/approve")
    @PreAuthorize("hasAuthority('EXECUTIVE_APPROVE')")
    @Operation(summary = "Approve: PENDING_EXECUTIVE_APPROVAL → EXECUTIVE_APPROVED")
    public FleetRequestDetailDto approve(@PathVariable Long id,
            @Valid @RequestBody(required = false) TransitionRequest body,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal) {
        return workflow.transition(id, WorkflowAction.APPROVE, body, principal);
    }

    @PostMapping("/reject")
    @PreAuthorize("hasAuthority('EXECUTIVE_APPROVE')")
    @Operation(summary = "Reject with a reason: PENDING_EXECUTIVE_APPROVAL → RETURNED_TO_MARKETING")
    public FleetRequestDetailDto reject(@PathVariable Long id, @Valid @RequestBody ReasonRequest body,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal) {
        return workflow.transition(id, WorkflowAction.REJECT, body.toTransition(), principal);
    }

    @PostMapping("/forward-to-finance")
    @PreAuthorize("hasAuthority('DEPOSIT_REVIEW')")
    @Operation(summary = "Deposits reviewed, forward: EXECUTIVE_APPROVED → PENDING_FINANCE_CLEARANCE")
    public FleetRequestDetailDto forwardToFinance(@PathVariable Long id,
            @Valid @RequestBody(required = false) TransitionRequest body,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal) {
        return workflow.transition(id, WorkflowAction.FORWARD_TO_FINANCE, body, principal);
    }
}
