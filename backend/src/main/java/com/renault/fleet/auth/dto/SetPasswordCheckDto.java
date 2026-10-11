package com.renault.fleet.auth.dto;

import java.time.Instant;

public record SetPasswordCheckDto(String username, Instant expiresAt) {
}
