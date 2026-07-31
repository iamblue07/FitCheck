package com.fitcheck.common.exception;

import org.springframework.http.HttpStatus;

public class ExternalServiceException extends AppException {

    public ExternalServiceException(String message) {
        super(message, HttpStatus.BAD_GATEWAY);
    }
}
