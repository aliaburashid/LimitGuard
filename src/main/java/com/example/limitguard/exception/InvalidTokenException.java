package com.example.limitguard.exception;

// Used when a verification token is invalid, expired or already used
public class InvalidTokenException extends RuntimeException {

    // Sends the error message to RuntimeException
    public InvalidTokenException(String message) {
        super(message);
    }
}