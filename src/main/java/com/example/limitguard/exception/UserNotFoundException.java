package com.example.limitguard.exception;

// custom exception used when a user cannot be found
public class UserNotFoundException extends RuntimeException {

    // passes the error message to RuntimeException
    public UserNotFoundException(String message) {
        super(message);
    }
}