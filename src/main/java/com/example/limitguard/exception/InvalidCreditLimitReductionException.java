package com.example.limitguard.exception;

// exception thrown when a credit limit is reduced below the current exposure
public class InvalidCreditLimitReductionException extends RuntimeException {

    public InvalidCreditLimitReductionException(String message) {
        super(message);
    }
}