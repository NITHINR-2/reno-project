package com.renault.fleet.catalog.controller;

import com.renault.fleet.catalog.dto.CreateVehicleModelRequest;
import com.renault.fleet.catalog.dto.CreateVehicleVariantRequest;
import com.renault.fleet.catalog.dto.UpdateVehicleModelRequest;
import com.renault.fleet.catalog.dto.UpdateVehicleVariantRequest;
import com.renault.fleet.catalog.dto.VehicleModelDto;
import com.renault.fleet.catalog.dto.VehicleModelFilter;
import com.renault.fleet.catalog.service.CatalogueAdminService;
import com.renault.fleet.catalog.service.VehicleModelService;
import com.renault.fleet.common.api.PageResponse;
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
@RequestMapping("/api/admin/catalogue")
@PreAuthorize("hasAuthority('CATALOGUE_MANAGE')")
@Tag(name = "Admin: catalogue")
public class CatalogueAdminController {

    private final VehicleModelService query;
    private final CatalogueAdminService service;

    public CatalogueAdminController(VehicleModelService query, CatalogueAdminService service) {
        this.query = query;
        this.service = service;
    }

    @GetMapping("/models")
    @Operation(summary = "List every model, active or not, with all variants")
    public PageResponse<VehicleModelDto> list(@ParameterObject VehicleModelFilter filter,
            @ParameterObject @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return query.list(filter, pageable);
    }

    @GetMapping("/models/{id}")
    @Operation(summary = "Get one model with all variants")
    public VehicleModelDto get(@PathVariable Long id) {
        return query.get(id);
    }

    @PostMapping("/models")
    @Operation(summary = "Create a model")
    public ResponseEntity<VehicleModelDto> create(@Valid @RequestBody CreateVehicleModelRequest body,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal) {
        VehicleModelDto created = service.createModel(body, principal);
        return ResponseEntity.created(URI.create("/api/admin/catalogue/models/" + created.id())).body(created);
    }

    @PatchMapping("/models/{id}")
    @Operation(summary = "Update a model; active=false takes it off the dealer catalogue")
    public VehicleModelDto update(@PathVariable Long id, @Valid @RequestBody UpdateVehicleModelRequest body,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal) {
        return service.updateModel(id, body, principal);
    }

    @PostMapping("/models/{id}/variants")
    @Operation(summary = "Add a variant to a model")
    public ResponseEntity<VehicleModelDto.Variant> createVariant(@PathVariable Long id,
            @Valid @RequestBody CreateVehicleVariantRequest body,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal) {
        VehicleModelDto.Variant created = service.createVariant(id, body, principal);
        return ResponseEntity.created(URI.create("/api/admin/catalogue/variants/" + created.id())).body(created);
    }

    @PatchMapping("/variants/{id}")
    @Operation(summary = "Update a variant's name, list price or active flag")
    public VehicleModelDto.Variant updateVariant(@PathVariable Long id, @Valid @RequestBody UpdateVehicleVariantRequest body,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal) {
        return service.updateVariant(id, body, principal);
    }
}
