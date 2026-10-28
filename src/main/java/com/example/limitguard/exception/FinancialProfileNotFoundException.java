package com.example.limitguard.exception;

// thrown when a company does not have a financial profile
public class FinancialProfileNotFoundException extends RuntimeException {

    public FinancialProfileNotFoundException(String message) {
        super(message);
    }
}