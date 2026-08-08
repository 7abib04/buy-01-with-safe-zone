package com.buy01.orderservice.exception;

public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(String productName) {
        super("Not enough stock available for: " + productName);
    }
}
