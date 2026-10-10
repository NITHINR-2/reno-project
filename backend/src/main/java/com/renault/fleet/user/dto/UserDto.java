package com.renault.fleet.user.dto;

import com.renault.fleet.user.entity.RoleCode;
import java.time.Instant;

/** A user as seen by the Admin Console. Never carries the password hash. */
public record UserDto(
        Long id,
        String username,
        String email,
        String fullName,
        RoleCode role,
        boolean active,
        Instant lastLoginAt,
        Long version,
        Instant createdAt,
        Instant updatedAt) {
}
