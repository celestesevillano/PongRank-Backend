package org.example.pongrankbackend.common.exception;

import org.springframework.http.HttpStatus;

public class InvalidCommunityException extends ApiException {
    public InvalidCommunityException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
