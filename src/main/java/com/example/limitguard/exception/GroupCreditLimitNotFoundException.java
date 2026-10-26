package com.example.limitguard.exception;

// Used when a requested group credit limit doesn't exist
public class GroupCreditLimitNotFoundException extends RuntimeException {

    public GroupCreditLimitNotFoundException(String message) {
        super(message);
    }
}
