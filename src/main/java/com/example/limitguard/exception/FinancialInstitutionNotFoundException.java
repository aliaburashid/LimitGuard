package com.example.limitguard.exception;

// Used when a financial institution cannot be found
public class FinancialInstitutionNotFoundException extends RuntimeException {

    // Sends the error message to RuntimeException
    public FinancialInstitutionNotFoundException(String message) {
        super(message);
    }
}