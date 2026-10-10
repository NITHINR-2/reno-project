package com.renault.fleet.common.error;

import org.springframework.http.HttpStatus;

/** 400: a request that is well-formed JSON but semantically invalid (e.g. a missing reason, duplicate lines). */
public class BadRequestException extends ApiException {

    public BadRequestException(String detail) {
        super(HttpStatus.BAD_REQUEST, "Bad Request", detail);
    }
}
