package com.example.limitguard.exception;

public class CorporateGroupAlreadyExistsException extends RuntimeException {

    // sends a message when a corporate group already has this name
    public CorporateGroupAlreadyExistsException(String message) {
        super(message);
    }
}
