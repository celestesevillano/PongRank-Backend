package org.example.pongrankbackend.common.exception;

import org.springframework.http.HttpStatus;

public class InvalidSearchException extends ApiException {
    public InvalidSearchException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
