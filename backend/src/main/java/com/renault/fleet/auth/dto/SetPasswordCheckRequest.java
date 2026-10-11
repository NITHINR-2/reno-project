package com.renault.fleet.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The token travels in a body, not a URL, so it stays out of access logs. */
public record SetPasswordCheckRequest(@NotBlank @Size(max = 200) String token) {
}
