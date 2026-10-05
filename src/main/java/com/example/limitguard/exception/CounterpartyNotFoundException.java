package com.example.limitguard.exception;

// exception used when a counterparty cannot be found
public class CounterpartyNotFoundException extends RuntimeException {

    public CounterpartyNotFoundException(String message) {
        super(message);
    }
}