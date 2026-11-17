package com.example.creditservice.exception;

public class CreditLimitAlreadyExistsException extends RuntimeException {

    public CreditLimitAlreadyExistsException(String message) {
        super(message);
    }
}