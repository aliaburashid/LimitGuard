package com.example.limitguard.exception;

// exception thrown when a credit request cannot be found
public class CreditRequestNotFoundException extends RuntimeException {

    public CreditRequestNotFoundException(String message) {
        super(message);
    }
}