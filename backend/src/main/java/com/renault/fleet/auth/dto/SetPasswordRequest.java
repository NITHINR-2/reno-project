package com.renault.fleet.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SetPasswordRequest(
        @NotBlank @Size(max = 200) String token,
        @NotBlank @Size(min = 12, max = 128, message = "must be 12-128 characters") String password) {
}
