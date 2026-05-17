package com.transport.optimizer.exception;

public class OrsApiException extends RuntimeException {
    public OrsApiException(String message) {
        super(message);
    }
    public OrsApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
