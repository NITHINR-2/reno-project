package com.renault.fleet.auth.dto;

import java.time.Instant;

/** Returned once to the Admin Console; the raw token is never stored or shown again. */
public record SetPasswordLinkDto(Long userId, String username, String url, Instant expiresAt) {
}
