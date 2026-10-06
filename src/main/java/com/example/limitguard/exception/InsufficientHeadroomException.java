package com.example.limitguard.exception;

// exception thrown when a credit request exceeds the available headroom
public class InsufficientHeadroomException extends RuntimeException{
    public InsufficientHeadroomException (String message) {
        super(message);
    }
}

