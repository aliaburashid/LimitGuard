package com.example.limitguard.exception;

public class CreditLimitAlreadyExistsException extends RuntimeException {

    public CreditLimitAlreadyExistsException(String message) {
        super(message);
    }
}