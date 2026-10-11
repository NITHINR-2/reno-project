package com.renault.fleet.user.controller;

import com.renault.fleet.common.api.PageResponse;
import com.renault.fleet.user.dto.RoleDto;
import com.renault.fleet.user.service.RoleQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Admin: roles")
public class AdminRoleController {

    private final RoleQueryService service;

    public AdminRoleController(RoleQueryService service) {
        this.service = service;
    }

    @GetMapping("/api/admin/roles")
    @PreAuthorize("hasAuthority('ADMIN_USERS_MANAGE')")
    @Operation(summary = "Roles and the permissions each grants (read-only)")
    public PageResponse<RoleDto> list(@ParameterObject @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return service.list(pageable);
    }
}
