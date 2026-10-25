package com.example.limitguard.exception;

public class CorporateGroupNotFoundException extends RuntimeException {

    // sends a message when the requested corporate group does not exist
    public CorporateGroupNotFoundException(String message) {
        super(message);
    }
}
