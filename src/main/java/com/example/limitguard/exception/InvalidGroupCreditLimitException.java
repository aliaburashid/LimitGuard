package com.example.limitguard.exception;

// Used when the credit limit amount is zero, negative, or missing, or the corporate group ID is missing
public class InvalidGroupCreditLimitException extends RuntimeException {

    public InvalidGroupCreditLimitException(String message) {
        super(message);
    }
}
