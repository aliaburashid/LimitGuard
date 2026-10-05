package com.example.limitguard.exception;

public class CreditLimitNotFoundException extends RuntimeException {

    public CreditLimitNotFoundException(String message) {
        super(message);
    }
}