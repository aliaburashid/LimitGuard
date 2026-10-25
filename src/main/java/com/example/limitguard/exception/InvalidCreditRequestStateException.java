package com.example.limitguard.exception;

/**
 * Thrown when a credit request violates its permitted workflow state transitions
 */
public class InvalidCreditRequestStateException extends RuntimeException {

    public InvalidCreditRequestStateException(String message) {
        super(message);
    }
}
