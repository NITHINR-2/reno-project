package com.renault.fleet.user.dto;

import com.renault.fleet.user.entity.RoleCode;
import java.util.List;

/** A role and the permissions it grants (read-only; roles come from migration V6). */
public record RoleDto(RoleCode code, String name, String description, List<PermissionDto> permissions) {

    public record PermissionDto(String code, String description) {
    }
}
