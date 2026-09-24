package org.example.pongrankbackend.common.exception;

import org.springframework.http.HttpStatus;

public class RatingCalculationException extends ApiException {
    public RatingCalculationException(String message) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
