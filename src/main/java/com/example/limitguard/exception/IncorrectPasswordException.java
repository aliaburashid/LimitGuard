package com.example.limitguard.exception;

// this exception is thrown when the user enters the wrong current password
public class IncorrectPasswordException extends RuntimeException{

    // passes the error message to RuntimeException
    public IncorrectPasswordException(String message) {
        super(message);
    }
}
