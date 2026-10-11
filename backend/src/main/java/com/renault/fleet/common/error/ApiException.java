package com.renault.fleet.common.error;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Base for expected business/API errors; rendered as a ProblemDetail with the given status, title and detail. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String title;
    private final Map<String, Object> properties = new LinkedHashMap<>();

    public ApiException(HttpStatus status, String title, String detail) {
        super(detail);
        this.status = status;
        this.title = title;
    }

    /** Adds a machine-readable member to the ProblemDetail (e.g. the units now available). */
    public ApiException with(String name, Object value) {
        properties.put(name, value);
        return this;
    }

    public Map<String, Object> getProperties() { return properties; }
    public HttpStatus getStatus() { return status; }
    public String getTitle() { return title; }
}
