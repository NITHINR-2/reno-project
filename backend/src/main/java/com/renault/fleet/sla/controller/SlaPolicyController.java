package com.renault.fleet.sla.controller;

import com.renault.fleet.common.api.PageResponse;
import com.renault.fleet.security.AuthenticatedUser;
import com.renault.fleet.sla.dto.SlaPolicyDto;
import com.renault.fleet.sla.dto.UpdateSlaPolicyRequest;
import com.renault.fleet.sla.service.SlaPolicyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/sla-policies")
@Tag(name = "Admin: SLA policies")
@PreAuthorize("hasAuthority('SLA_POLICY_MANAGE')")
public class SlaPolicyController {

    private final SlaPolicyService service;

    public SlaPolicyController(SlaPolicyService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "SLA policy per workflow stage (Supervisor only)")
    public PageResponse<SlaPolicyDto> list(@ParameterObject @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return service.list(pageable);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Change hours allowed, warning threshold or active flag (audited; applies to stages entered afterwards)")
    public SlaPolicyDto update(@PathVariable Long id, @Valid @RequestBody UpdateSlaPolicyRequest body,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal) {
        return service.update(id, body, principal);
    }
}
