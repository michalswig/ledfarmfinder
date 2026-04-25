package com.mike.leadfarmfinder.service.ses.exception;

public class SesEventProcessingException extends RuntimeException {

    public SesEventProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}