package com.example.authservice.exception;

public class FinancialInstitutionAlreadyExistsException extends RuntimeException {

    public FinancialInstitutionAlreadyExistsException(String message) {
        super(message);
    }
}