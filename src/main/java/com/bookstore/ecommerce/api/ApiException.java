package com.bookstore.ecommerce.api;

import org.springframework.http.HttpStatus;

/**
 * An error with the HTTP status the API should answer with. {@code field} optionally names the form
 * field the message belongs to, so the frontend can show it beside that input.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String field;

    public ApiException(HttpStatus status, String message) {
        this(status, message, null);
    }

    public ApiException(HttpStatus status, String message, String field) {
        super(message);
        this.status = status;
        this.field = field;
    }

    public static ApiException badRequest(String field, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message, field);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getField() {
        return field;
    }
}
