package org.example.pongrankbackend.common.exception;

import org.springframework.http.HttpStatus;

public class EmailDeliveryException extends ApiException {
    public EmailDeliveryException(String message) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
