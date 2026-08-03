package com.fitcheck.common.exception;

import org.springframework.http.HttpStatus;

public class InvalidPhotoTypeException extends AppException {

    public InvalidPhotoTypeException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}