package com.example.limitguard.exception;

// Used when someone tries to register with an email that already exists
public class EmailAlreadyExistsException extends RuntimeException {

    // Sends the error message to RuntimeException
    public EmailAlreadyExistsException(String message) {
        super(message);
    }
}
