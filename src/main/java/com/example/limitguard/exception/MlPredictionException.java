package com.example.limitguard.exception;

// thrown when the python ml prediction service fails
public class MlPredictionException extends RuntimeException {

    public MlPredictionException(String message) {
        super(message);
    }

    public MlPredictionException(String message, Throwable cause) {
        super(message, cause);
    }
}