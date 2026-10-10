package com.renault.fleet.auth.service;

import com.renault.fleet.common.error.ApiException;
import org.springframework.http.HttpStatus;

/** Missing, unknown, expired, revoked or reused refresh token. The handler also clears the refresh cookie. */
public class InvalidRefreshTokenException extends ApiException {

    public InvalidRefreshTokenException() {
        super(HttpStatus.UNAUTHORIZED, "Unauthorized", "The session has expired. Please sign in again.");
    }
}
