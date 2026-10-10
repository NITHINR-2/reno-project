package com.renault.fleet.common.error;

import org.springframework.http.HttpStatus;

/** 409: invalid workflow transition, duplicate approval, or another state conflict. */
public class ConflictException extends ApiException {

    public ConflictException(String detail) {
        super(HttpStatus.CONFLICT, "Conflict", detail);
    }
}
