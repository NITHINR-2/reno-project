package com.renault.fleet.user.dto;

import com.renault.fleet.user.entity.RoleCode;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Partial update: a null field means "leave unchanged". Activation has its own endpoints. */
public record UpdateUserRequest(
        @NotNull Long version,
        @Email @Size(max = 255) String email,
        @Size(min = 1, max = 150) String fullName,
        RoleCode role) {
}
