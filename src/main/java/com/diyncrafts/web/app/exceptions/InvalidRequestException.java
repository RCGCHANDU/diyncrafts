package com.diyncrafts.web.app.exceptions;

/**
 * Thrown for semantically invalid input that Bean Validation cannot express (HTTP 400).
 */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
