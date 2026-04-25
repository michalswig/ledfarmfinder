package com.mike.leadfarmfinder.service.ses.exception;

public class SesEventBadRequestException extends RuntimeException {

    public SesEventBadRequestException(String message) {
        super(message);
    }

    public SesEventBadRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}