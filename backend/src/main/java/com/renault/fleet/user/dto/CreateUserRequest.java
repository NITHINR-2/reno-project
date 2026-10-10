package com.renault.fleet.user.dto;

import com.renault.fleet.user.entity.RoleCode;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** The initial password is hashed with BCrypt and never stored, logged or audited in clear. */
public record CreateUserRequest(
        @NotBlank @Pattern(regexp = "^[a-z0-9][a-z0-9._-]{2,59}$",
                message = "3-60 chars: lower-case letters, digits and . _ -") String username,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 150) String fullName,
        @NotNull RoleCode role,
        @NotBlank @Size(min = 12, max = 128, message = "must be 12-128 characters") String password) {
}
