package com.example.limitguard.exception;

/**
 * Thrown when a credit request violates a credit capacity or counterparty business rule
 */
public class InvalidCreditRequestOperationException extends RuntimeException {

    public InvalidCreditRequestOperationException(String message) {
        super(message);
    }
}
