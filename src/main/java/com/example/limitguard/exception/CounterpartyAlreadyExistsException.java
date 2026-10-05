package com.example.limitguard.exception;

// duplicate counterparty names are not allowed
public class CounterpartyAlreadyExistsException extends RuntimeException {

    public CounterpartyAlreadyExistsException(String message) {
        super(message);
    }
}