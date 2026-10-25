package com.example.limitguard.exception;

public class InvalidCorporateGroupException extends RuntimeException {

    // handles invalid corporate group information
    public InvalidCorporateGroupException(String message) {
        super(message);
    }
}
