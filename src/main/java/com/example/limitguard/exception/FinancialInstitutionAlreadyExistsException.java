package com.example.limitguard.exception;

public class FinancialInstitutionAlreadyExistsException extends RuntimeException {

    public FinancialInstitutionAlreadyExistsException(String message) {
        super(message);
    }
}