package com.renault.fleet.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Size(max = 255) String username,
        @NotBlank @Size(max = 200) String password) {

    /** Never log the password. */
    @Override
    public String toString() {
        return "LoginRequest[username=" + username + "]";
    }
}
