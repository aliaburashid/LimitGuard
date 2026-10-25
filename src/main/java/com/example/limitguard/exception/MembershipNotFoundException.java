package com.example.limitguard.exception;

// used when the membership cannot be found in the database
public class MembershipNotFoundException extends RuntimeException {

    // sends the error message to the parent exception class
    public MembershipNotFoundException(String message) {
        super(message);
    }
}