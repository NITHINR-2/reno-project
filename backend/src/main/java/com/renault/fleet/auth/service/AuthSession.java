package com.renault.fleet.auth.service;

import com.renault.fleet.auth.dto.AuthUserDto;

/** Result of a successful login or refresh: the access token for the body and the raw refresh token for the cookie. */
public record AuthSession(String accessToken, long expiresInSeconds, String refreshToken, AuthUserDto user) {

    @Override
    public String toString() {
        return "AuthSession[user=" + user.username() + "]";
    }
}
