package com.fitcheck.common.exception;

import org.springframework.http.HttpStatus;

public class RateLimitExceededException extends AppException {

    public RateLimitExceededException(String message) {
        super(message, HttpStatus.TOO_MANY_REQUESTS);
    }
}