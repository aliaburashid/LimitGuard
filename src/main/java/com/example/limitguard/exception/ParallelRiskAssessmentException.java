package com.example.limitguard.exception;

// thrown when the parallel risk assessment cannot complete
public class ParallelRiskAssessmentException extends RuntimeException {

    public ParallelRiskAssessmentException(String message) {
        super(message);
    }

    public ParallelRiskAssessmentException(String message, Throwable cause) {
        super(message, cause);
    }
}