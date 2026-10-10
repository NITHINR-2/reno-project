package com.renault.fleet.auth.dto;

/** Body of login and refresh. The refresh token travels only in the HttpOnly cookie, never in JSON. */
public record TokenResponse(String accessToken, String tokenType, long expiresIn, AuthUserDto user) {

    public static TokenResponse bearer(String accessToken, long expiresIn, AuthUserDto user) {
        return new TokenResponse(accessToken, "Bearer", expiresIn, user);
    }
}
