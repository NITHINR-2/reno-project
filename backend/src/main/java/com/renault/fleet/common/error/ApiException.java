package com.renault.fleet.common.error;

import org.springframework.http.HttpStatus;

/** Base for expected business/API errors; rendered as a ProblemDetail with the given status, title and detail. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String title;

    public ApiException(HttpStatus status, String title, String detail) {
        super(detail);
        this.status = status;
        this.title = title;
    }

    public HttpStatus getStatus() { return status; }
    public String getTitle() { return title; }
}
