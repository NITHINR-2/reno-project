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
@Tag(name = "Finance: clearance")
public class FinanceWorkflowController {

    private final WorkflowService workflow;

    public FinanceWorkflowController(WorkflowService workflow) {
        this.workflow = workflow;
    }

    @PostMapping("/grant-clearance")
    @PreAuthorize("hasAuthority('FINANCE_CLEARANCE')")
    @Operation(summary = "Grant financial clearance: FUNDS_VERIFIED → FINANCIALLY_CLEARED (deposit must still be covered)")
    public FleetRequestDetailDto grantClearance(@PathVariable Long id,
            @Valid @RequestBody(required = false) TransitionRequest body,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal) {
        return workflow.transition(id, WorkflowAction.GRANT_CLEARANCE, body, principal);
    }
}
