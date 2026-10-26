package com.example.limitguard.exception;

// Used when a corporate group already has a credit limit
public class GroupCreditLimitAlreadyExistsException extends RuntimeException {

    public GroupCreditLimitAlreadyExistsException(String message) {
        super(message);
    }
}
