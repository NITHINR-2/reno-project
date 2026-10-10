package com.renault.fleet.dealer.controller;

import com.renault.fleet.common.api.PageResponse;
import com.renault.fleet.dealer.dto.CreateDealerDto;
import com.renault.fleet.dealer.dto.DealerDto;
import com.renault.fleet.dealer.dto.DealerFilter;
import com.renault.fleet.dealer.dto.UpdateDealerDto;
import com.renault.fleet.dealer.service.DealerService;
import com.renault.fleet.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dealers")
@Tag(name = "Dealers")
public class DealerController {

    private final DealerService service;

    public DealerController(DealerService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('FLEET_REQUEST_READ', 'ADMIN_DEALERS_MANAGE')")
    @Operation(summary = "List dealers (filter by name/code, region, status)")
    public PageResponse<DealerDto> list(@ParameterObject DealerFilter filter,
            @ParameterObject @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return service.list(filter, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('FLEET_REQUEST_READ', 'ADMIN_DEALERS_MANAGE')")
    @Operation(summary = "Get one dealer")
    public DealerDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN_DEALERS_MANAGE')")
    @Operation(summary = "Onboard a dealer (starts in PENDING_ONBOARDING)")
    public ResponseEntity<DealerDto> create(@Valid @RequestBody CreateDealerDto body,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal) {
        DealerDto created = service.create(body, principal);
        return ResponseEntity.created(URI.create("/api/dealers/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN_DEALERS_MANAGE')")
    @Operation(summary = "Update a dealer, including activation or suspension (null fields are left unchanged)")
    public DealerDto update(@PathVariable Long id, @Valid @RequestBody UpdateDealerDto body,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal) {
        return service.update(id, body, principal);
    }
}
