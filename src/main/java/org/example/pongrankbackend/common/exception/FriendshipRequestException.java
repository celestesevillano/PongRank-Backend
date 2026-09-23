package org.example.pongrankbackend.common.exception;

import org.springframework.http.HttpStatus;

public class FriendshipRequestException extends ApiException {
    public FriendshipRequestException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
