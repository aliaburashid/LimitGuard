package com.example.limitguard.exception;

// Used when a user tries to log in before verifying their email
public class EmailNotVerifiedException extends RuntimeException {

    // Sends the error message to RuntimeException
    public EmailNotVerifiedException(String message) {
        super(message);
    }
}