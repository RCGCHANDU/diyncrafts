package com.diyncrafts.web.app.exceptions;

/**
 * Thrown when a request conflicts with existing state, e.g. a duplicate name (HTTP 409).
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
