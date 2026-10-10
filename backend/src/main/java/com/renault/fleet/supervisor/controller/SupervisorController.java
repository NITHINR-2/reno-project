package com.renault.fleet.supervisor.controller;

import com.renault.fleet.fleetrequest.dto.FleetRequestDetailDto;
import com.renault.fleet.fleetrequest.dto.ReasonRequest;
import com.renault.fleet.security.AuthenticatedUser;
import com.renault.fleet.supervisor.dto.OverrideRequest;
import com.renault.fleet.supervisor.dto.ReassignRequest;
import com.renault.fleet.supervisor.service.SupervisorService;
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
@Tag(name = "Supervisor: overrides")
public class SupervisorController {

    private final SupervisorService service;
    private final WorkflowService workflow;

    public SupervisorController(SupervisorService service, WorkflowService workflow) {
        this.service = service;
        this.workflow = workflow;
    }

    @PostMapping("/override")
    @PreAuthorize("hasAuthority('WORKFLOW_OVERRIDE')")
    @Operation(summary = "Override allow-listed fields with a reason (audited before/after)",
            description = "Allowed fields: requiredDeposit, productionEstimateWeeks, requestedDeliveryDate, plantId, notes. "
                    + "Any other field is a 400. Status, quantities, stock, tax and receipts cannot be overridden.")
    public FleetRequestDetailDto override(@PathVariable Long id, @Valid @RequestBody OverrideRequest body,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal) {
        return service.override(id, body, principal);
    }

    @PostMapping("/reassign")
    @PreAuthorize("hasAuthority('REQUEST_REASSIGN')")
    @Operation(summary = "Reassign the request to another active fleet user (reason required)")
    public FleetRequestDetailDto reassign(@PathVariable Long id, @Valid @RequestBody ReassignRequest body,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal) {
        return service.reassign(id, body, principal);
    }

    @PostMapping("/cancel")
    @PreAuthorize("hasAuthority('REQUEST_CANCEL')")
    @Operation(summary = "Cancel a non-terminal request with a reason (releases stock, voids open quotations and orders)")
    public FleetRequestDetailDto cancel(@PathVariable Long id, @Valid @RequestBody ReasonRequest body,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal) {
        return workflow.transition(id, WorkflowAction.CANCEL, body.toTransition(), principal);
    }
}
