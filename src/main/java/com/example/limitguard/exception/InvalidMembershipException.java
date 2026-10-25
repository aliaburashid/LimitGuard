package com.example.limitguard.exception;

// used when the membership information is missing or invalid
public class InvalidMembershipException extends RuntimeException {

    // sends the error message to the parent exception class
    public InvalidMembershipException(String message) {
        super(message);
    }
}