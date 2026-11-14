package org.example.pongrankbackend.common.exception;

import org.springframework.http.HttpStatus;

public class InvalidSearchTermException extends ApiException {
    public InvalidSearchTermException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
