package com.renault.fleet.user.dto;

import com.renault.fleet.user.entity.RoleCode;

/** Query-string filters for {@code GET /api/admin/users}; {@code q} matches username, name or email. */
public record UserFilter(String q, RoleCode role, Boolean active) {
}
