package com.buy01.orderservice.exception;

public class RemoteServiceException extends RuntimeException {

    public RemoteServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
