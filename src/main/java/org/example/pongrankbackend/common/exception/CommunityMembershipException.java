package org.example.pongrankbackend.common.exception;

import org.springframework.http.HttpStatus;

public class CommunityMembershipException extends ApiException {
    public CommunityMembershipException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
