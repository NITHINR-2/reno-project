package com.renault.fleet.dealer.controller;

import com.renault.fleet.common.api.PageResponse;
import com.renault.fleet.dealer.dto.RegionDto;
import com.renault.fleet.dealer.service.RegionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only reference list for filters and forms. */
@RestController
@Tag(name = "Reference data")
public class RegionController {

    private final RegionService service;

    public RegionController(RegionService service) {
        this.service = service;
    }

    @GetMapping("/api/regions")
    @PreAuthorize("hasAnyAuthority('FLEET_REQUEST_READ', 'ADMIN_DEALERS_MANAGE')")
    @Operation(summary = "List regions")
    public PageResponse<RegionDto> list(@ParameterObject @PageableDefault(size = 100, sort = "name") Pageable pageable) {
        return service.list(pageable);
    }
}
