package com.renault.fleet.security;

import com.renault.fleet.common.error.ApiException;
import org.springframework.http.HttpStatus;

public class MissingCsrfHeaderException extends ApiException {

    public MissingCsrfHeaderException() {
        super(HttpStatus.FORBIDDEN, "Forbidden", "The X-Requested-With header is required for this request.");
    }
}
