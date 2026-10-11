package com.renault.fleet.user.controller;

import com.renault.fleet.auth.dto.SetPasswordLinkDto;
import com.renault.fleet.auth.service.SetPasswordService;
import com.renault.fleet.common.api.PageResponse;
import com.renault.fleet.security.AuthenticatedUser;
import com.renault.fleet.user.dto.CreateUserRequest;
import com.renault.fleet.user.dto.UpdateUserRequest;
import com.renault.fleet.user.dto.UserDto;
import com.renault.fleet.user.dto.UserFilter;
import com.renault.fleet.user.service.UserAdminService;
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
@RequestMapping("/api/admin/users")
@Tag(name = "Admin: users")
@PreAuthorize("hasAuthority('ADMIN_USERS_MANAGE')")
public class AdminUserController {

    private final UserAdminService service;
    private final SetPasswordService setPasswordService;

    public AdminUserController(UserAdminService service, SetPasswordService setPasswordService) {
        this.service = service;
        this.setPasswordService = setPasswordService;
    }

    @GetMapping
    @Operation(summary = "List users (filter by text, role, active)")
    public PageResponse<UserDto> list(@ParameterObject UserFilter filter,
            @ParameterObject @PageableDefault(size = 20, sort = "username") Pageable pageable) {
        return service.list(filter, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one user")
    public UserDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @Operation(summary = "Create a user (only a SUPERVISOR can create SUPERVISOR accounts)")
    public ResponseEntity<UserDto> create(@Valid @RequestBody CreateUserRequest body,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal) {
        UserDto created = service.create(body, principal);
        return ResponseEntity.created(URI.create("/api/admin/users/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update name, email or role (not your own role)")
    public UserDto update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest body,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal) {
        return service.update(id, body, principal);
    }

    @PostMapping("/{id}/activate")
    @Operation(summary = "Activate a user")
    public UserDto activate(@PathVariable Long id,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal) {
        return service.setActive(id, true, principal);
    }

    @PostMapping("/{id}/set-password-link")
    @Operation(summary = "Issue a one-time set-password link (shown once; replaces the user's older links)")
    public SetPasswordLinkDto setPasswordLink(@PathVariable Long id,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal) {
        return setPasswordService.issue(id, principal);
    }

    @PostMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate a user (revokes refresh tokens; access tokens stop working immediately)")
    public UserDto deactivate(@PathVariable Long id,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal) {
        return service.setActive(id, false, principal);
    }
}
