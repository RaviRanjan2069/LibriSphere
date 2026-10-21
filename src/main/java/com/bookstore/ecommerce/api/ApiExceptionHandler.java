package com.bookstore.ecommerce.api;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import java.util.LinkedHashMap;
import java.util.Map;

/** Turns exceptions from the API controllers into {"error": "...", "field": "..."} JSON responses. */
@RestControllerAdvice(basePackages = "com.bookstore.ecommerce.api")
public class ApiExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, String>> api(ApiException e) {
        return error(e.getStatus(), e.getMessage(), e.getField());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return error(HttpStatus.BAD_REQUEST, e.getMessage(), null);
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, String>> forbidden(SecurityException e) {
        return error(HttpStatus.FORBIDDEN, e.getMessage(), null);
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<Map<String, String>> duplicate(DuplicateKeyException e) {
        return error(HttpStatus.CONFLICT, "That username or email is already registered.", null);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> unreadable(HttpMessageNotReadableException e) {
        return error(HttpStatus.BAD_REQUEST, "The request body is not valid JSON.", null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> tooLarge(MaxUploadSizeExceededException e) {
        return error(HttpStatus.PAYLOAD_TOO_LARGE, "Profile pictures must be 2 MB or smaller.", "avatar");
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<Map<String, String>> multipart(MultipartException e) {
        return error(HttpStatus.BAD_REQUEST, "The upload could not be read. Please try again.", "avatar");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, String>> missing(MissingServletRequestParameterException e) {
        return error(HttpStatus.BAD_REQUEST, "Missing required field: " + e.getParameterName() + ".", e.getParameterName());
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String message, String field) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("error", message == null ? status.getReasonPhrase() : message);
        if (field != null) {
            body.put("field", field);
        }
        return ResponseEntity.status(status).body(body);
    }
}
