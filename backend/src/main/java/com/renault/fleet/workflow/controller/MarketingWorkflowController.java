package com.renault.fleet.workflow.controller;

import com.renault.fleet.fleetrequest.dto.FleetRequestDetailDto;
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
@Tag(name = "Marketing: workflow")
public class MarketingWorkflowController {

    private final WorkflowService workflow;

    public MarketingWorkflowController(WorkflowService workflow) {
        this.workflow = workflow;
    }

    @PostMapping("/submit-for-qualification")
    @PreAuthorize("hasAuthority('MARKETING_FORWARD')")
    @Operation(summary = "Qualify: PENDING_MARKETING_REVIEW → MARKETING_QUALIFIED (stock must be mapped first)")
    public FleetRequestDetailDto submitForQualification(@PathVariable Long id,
            @Valid @RequestBody(required = false) TransitionRequest body,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal) {
        return workflow.transition(id, WorkflowAction.QUALIFY, body, principal);
    }

    @PostMapping("/forward-to-marketing-head")
    @PreAuthorize("hasAuthority('MARKETING_FORWARD')")
    @Operation(summary = "Forward: MARKETING_QUALIFIED → PENDING_EXECUTIVE_APPROVAL")
    public FleetRequestDetailDto forwardToMarketingHead(@PathVariable Long id,
            @Valid @RequestBody(required = false) TransitionRequest body,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal) {
        return workflow.transition(id, WorkflowAction.FORWARD_TO_EXECUTIVE, body, principal);
    }

    @PostMapping("/resubmit")
    @PreAuthorize("hasAuthority('MARKETING_FORWARD')")
    @Operation(summary = "Resubmit a returned request: RETURNED_TO_MARKETING → PENDING_MARKETING_REVIEW")
    public FleetRequestDetailDto resubmit(@PathVariable Long id,
            @Valid @RequestBody(required = false) TransitionRequest body,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal) {
        return workflow.transition(id, WorkflowAction.RESUBMIT, body, principal);
    }
}
