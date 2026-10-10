package com.renault.fleet.auth.dto;

import com.renault.fleet.user.entity.RoleCode;
import java.util.List;

/** The signed-in user as the frontend sees it. Permissions are for UI mirroring only; the server re-checks. */
public record AuthUserDto(Long id, String username, String fullName, String email, RoleCode role, List<String> permissions) {
}
