package org.example.pongrankbackend.common.exception;

import org.springframework.http.HttpStatus;

public class PaymentProcessingException extends ApiException {
    public PaymentProcessingException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
