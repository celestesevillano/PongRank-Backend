package org.example.pongrankbackend.common.exception;

import org.springframework.http.HttpStatus;

public class InvalidProfileException extends ApiException {
    public InvalidProfileException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
