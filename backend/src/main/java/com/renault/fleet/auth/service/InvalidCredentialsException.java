package com.renault.fleet.auth.service;

import com.renault.fleet.common.error.ApiException;
import org.springframework.http.HttpStatus;

/** Same response for unknown user, wrong password and inactive account, so accounts can't be enumerated. */
public class InvalidCredentialsException extends ApiException {

    public InvalidCredentialsException() {
        super(HttpStatus.UNAUTHORIZED, "Unauthorized", "Invalid username or password.");
    }
}
