package org.example.pongrankbackend.common.exception;

import org.springframework.http.HttpStatus;

public class InvalidMatchStateException extends ApiException {
    public InvalidMatchStateException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
