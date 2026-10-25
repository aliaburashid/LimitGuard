package com.example.limitguard.exception;

// used when a counterparty already belongs to a corporate group
public class MembershipExistsException extends RuntimeException {

    // sends the error message to the parent exception class
    public MembershipExistsException(String message) {
        super(message);
    }
}