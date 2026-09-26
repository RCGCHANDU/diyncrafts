package com.diyncrafts.web.app.exceptions;

/**
 * Object storage (S3) could not complete an operation. Mapped to HTTP 503 without exposing details.
 */
public class StorageException extends RuntimeException {

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
